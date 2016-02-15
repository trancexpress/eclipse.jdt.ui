/*******************************************************************************
 * Copyright (c) 2000, 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ui;

import java.util.Iterator;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.ListenerList;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceStatus;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.IBaseLabelProvider;
import org.eclipse.jface.viewers.IDecoration;
import org.eclipse.jface.viewers.ILabelDecorator;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ILightweightLabelDecorator;
import org.eclipse.jface.viewers.LabelProviderChangedEvent;

import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.IAnnotationModel;

import org.eclipse.ui.part.FileEditorInput;

import org.eclipse.ui.texteditor.MarkerAnnotation;

import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaModelMarker;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.launching.JavaRuntime;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.viewsupport.IProblemChangedListener;
import org.eclipse.jdt.internal.ui.viewsupport.ImageDescriptorRegistry;
import org.eclipse.jdt.internal.ui.viewsupport.ImageImageDescriptor;

/**
 * LabelDecorator that decorates an element's image with error and warning overlays that
 * represent the severity of markers attached to the element's underlying resource. To see
 * a problem decoration for a marker, the marker needs to be a subtype of <code>IMarker.PROBLEM</code>.
 * <p>
 * <b>Important</b>: Although this decorator implements ILightweightLabelDecorator, do not contribute this
 * class as a decorator to the <code>org.eclipse.ui.decorators</code> extension. Only use this class in your
 * own views and label providers.
 *
 * @since 2.0
 */
public class ProblemsLabelDecorator implements ILabelDecorator, ILightweightLabelDecorator {

	/**
	 * This is a special <code>LabelProviderChangedEvent</code> carrying additional
	 * information whether the event origins from a maker change.
	 * <p>
	 * <code>ProblemsLabelChangedEvent</code>s are only generated by <code>
	 * ProblemsLabelDecorator</code>s.
	 * </p>
	 */
	public static class ProblemsLabelChangedEvent extends LabelProviderChangedEvent {

		private static final long serialVersionUID= 1L;

		private boolean fMarkerChange;

		/**
		 * @param eventSource the base label provider
		 * @param changedResource the changed resources
		 * @param isMarkerChange <code>true</code> if the change is a marker change; otherwise
		 *  <code>false</code>
		 */
		public ProblemsLabelChangedEvent(IBaseLabelProvider eventSource, IResource[] changedResource, boolean isMarkerChange) {
			super(eventSource, changedResource);
			fMarkerChange= isMarkerChange;
		}

		/**
		 * Returns whether this event origins from marker changes. If <code>false</code> an annotation
		 * model change is the origin. In this case viewers not displaying working copies can ignore these
		 * events.
		 *
		 * @return if this event origins from a marker change.
		 */
		public boolean isMarkerChange() {
			return fMarkerChange;
		}

	}

	private static final int ERRORTICK_WARNING= JavaElementImageDescriptor.WARNING;
	private static final int ERRORTICK_ERROR= JavaElementImageDescriptor.ERROR;
	private static final int ERRORTICK_BUILDPATH_ERROR= JavaElementImageDescriptor.BUILDPATH_ERROR;
	private static final int ERRORTICK_IGNORE_OPTIONAL_PROBLEMS= JavaElementImageDescriptor.IGNORE_OPTIONAL_PROBLEMS;
	private static final int ERRORTICK_INFO= JavaElementImageDescriptor.INFO;

	private ImageDescriptorRegistry fRegistry;
	private boolean fUseNewRegistry= false;
	private IProblemChangedListener fProblemChangedListener;

	private ListenerList<ILabelProviderListener> fListeners;
	private ISourceRange fCachedRange;

	/**
	 * Creates a new <code>ProblemsLabelDecorator</code>.
	 */
	public ProblemsLabelDecorator() {
		this(null);
		fUseNewRegistry= true;
	}

	/**
	 * Note: This constructor is for internal use only. Clients should not call this constructor.
	 *
	 * @param registry The registry to use or <code>null</code> to use the Java plugin's image
	 *            registry
	 * @noreference This constructor is not intended to be referenced by clients.
	 */
	public ProblemsLabelDecorator(ImageDescriptorRegistry registry) {
		fRegistry= registry;
		fProblemChangedListener= null;
	}

	private ImageDescriptorRegistry getRegistry() {
		if (fRegistry == null) {
			fRegistry= fUseNewRegistry ? new ImageDescriptorRegistry() : JavaPlugin.getImageDescriptorRegistry();
		}
		return fRegistry;
	}


	@Override
	public String decorateText(String text, Object element) {
		return text;
	}

	@Override
	public Image decorateImage(Image image, Object obj) {
		if (image == null)
			return null;

		int adornmentFlags= computeAdornmentFlags(obj);
		if (adornmentFlags != 0) {
			ImageDescriptor baseImage= new ImageImageDescriptor(image);
			Rectangle bounds= image.getBounds();
			return getRegistry().get(new JavaElementImageDescriptor(baseImage, adornmentFlags, new Point(bounds.width, bounds.height)));
		}
		return image;
	}

	/**
	 * Computes the adornment flags for the given element.
	 *
	 * @param obj the element to compute the flags for
	 *
	 * @return the adornment flags
	 */
	protected int computeAdornmentFlags(Object obj) {
		try {
			if (obj instanceof IJavaElement) {
				IJavaElement element= (IJavaElement) obj;
				int type= element.getElementType();
				switch (type) {
					case IJavaElement.JAVA_MODEL:
					case IJavaElement.JAVA_PROJECT:
					case IJavaElement.PACKAGE_FRAGMENT_ROOT:
						int flags= getErrorTicksFromMarkers(element.getResource(), IResource.DEPTH_INFINITE, null);
						switch (type) {
							case IJavaElement.PACKAGE_FRAGMENT_ROOT:
								IPackageFragmentRoot root= (IPackageFragmentRoot) element;
								if (flags != ERRORTICK_ERROR && root.getKind() == IPackageFragmentRoot.K_SOURCE && isIgnoringOptionalProblems(root.getRawClasspathEntry())) {
									flags= ERRORTICK_IGNORE_OPTIONAL_PROBLEMS;
								}
								break;
							case IJavaElement.JAVA_PROJECT:
								IJavaProject project= (IJavaProject) element;
								if (flags != ERRORTICK_ERROR && flags != ERRORTICK_BUILDPATH_ERROR && isIgnoringOptionalProblems(project)) {
									flags= ERRORTICK_IGNORE_OPTIONAL_PROBLEMS;
								}
								break;
						}
						return flags;
					case IJavaElement.PACKAGE_FRAGMENT:
						return getPackageErrorTicksFromMarkers((IPackageFragment) element);
					case IJavaElement.COMPILATION_UNIT:
					case IJavaElement.CLASS_FILE:
						return getErrorTicksFromMarkers(element.getResource(), IResource.DEPTH_ONE, null);
					case IJavaElement.PACKAGE_DECLARATION:
					case IJavaElement.IMPORT_DECLARATION:
					case IJavaElement.IMPORT_CONTAINER:
					case IJavaElement.TYPE:
					case IJavaElement.INITIALIZER:
					case IJavaElement.METHOD:
					case IJavaElement.FIELD:
					case IJavaElement.LOCAL_VARIABLE:
						ICompilationUnit cu= (ICompilationUnit) element.getAncestor(IJavaElement.COMPILATION_UNIT);
						if (cu != null) {
							ISourceReference ref= (type == IJavaElement.COMPILATION_UNIT) ? null : (ISourceReference) element;
							// The assumption is that only source elements in compilation unit can have markers
							IAnnotationModel model= isInJavaAnnotationModel(cu);
							int result= 0;
							if (model != null) {
								// open in Java editor: look at annotation model
								result= getErrorTicksFromAnnotationModel(model, ref);
							} else {
								result= getErrorTicksFromMarkers(cu.getResource(), IResource.DEPTH_ONE, ref);
							}
							fCachedRange= null;
							return result;
						}
						break;
					default:
				}
			} else if (obj instanceof IResource) {
				return getErrorTicksFromMarkers((IResource) obj, IResource.DEPTH_INFINITE, null);
			}
		} catch (CoreException e) {
			if (e instanceof JavaModelException) {
				if (((JavaModelException) e).isDoesNotExist()) {
					return 0;
				}
			}
			if (e.getStatus().getCode() == IResourceStatus.MARKER_NOT_FOUND) {
				return 0;
			}

			JavaPlugin.log(e);
		}
		return 0;
	}

	private boolean isIgnoringOptionalProblems(IClasspathEntry entry) {
		if (entry.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
			IClasspathAttribute[] extraAttributes= entry.getExtraAttributes();
			for (int i= 0; i < extraAttributes.length; i++) {
				IClasspathAttribute attrib= extraAttributes[i];
				if (IClasspathAttribute.IGNORE_OPTIONAL_PROBLEMS.equals(attrib.getName())) {
					return "true".equals(attrib.getValue()); //$NON-NLS-1$
				}
			}
		}
		return false;
	}
	
	private boolean isIgnoringOptionalProblems(IJavaProject project) throws JavaModelException {
		IPath projectPath= project.getPath();
		IClasspathEntry[] rawClasspath= project.getRawClasspath();
		for (IClasspathEntry entry : rawClasspath) {
			if (entry.getEntryKind() == IClasspathEntry.CPE_SOURCE && projectPath.equals(entry.getPath()) && isIgnoringOptionalProblems(entry))
				return true;
		}
		return false;
	}

	private int getErrorTicksFromMarkers(IResource res, int depth, ISourceReference sourceElement) throws CoreException {
		if (res == null || !res.isAccessible()) {
			return 0;
		}
		int severity= -1;
		if (sourceElement == null) {
			if (res instanceof IProject) {
				severity= res.findMaxProblemSeverity(IJavaModelMarker.BUILDPATH_PROBLEM_MARKER, true, IResource.DEPTH_ZERO);
				if (severity == IMarker.SEVERITY_ERROR) {
					return ERRORTICK_BUILDPATH_ERROR;
				}
				severity= res.findMaxProblemSeverity(JavaRuntime.JRE_CONTAINER_MARKER, true, IResource.DEPTH_ZERO);
				if (severity == IMarker.SEVERITY_ERROR) {
					return ERRORTICK_BUILDPATH_ERROR;
				}
			}
			severity= res.findMaxProblemSeverity(IMarker.PROBLEM, true, depth);
		} else {
			IMarker[] markers= res.findMarkers(IMarker.PROBLEM, true, depth);
			if (markers != null && markers.length > 0) {
				for (int i= 0; i < markers.length && (severity != IMarker.SEVERITY_ERROR); i++) {
					IMarker curr= markers[i];
					if (isMarkerInRange(curr, sourceElement)) {
						int val= curr.getAttribute(IMarker.SEVERITY, -1);
						if (val == IMarker.SEVERITY_INFO || val == IMarker.SEVERITY_WARNING || val == IMarker.SEVERITY_ERROR) {
							severity= val;
						}
					}
				}
			}
		}
		if (severity == IMarker.SEVERITY_ERROR) {
			return ERRORTICK_ERROR;
		} else if (severity == IMarker.SEVERITY_WARNING) {
			return ERRORTICK_WARNING;
		} else if (severity == IMarker.SEVERITY_INFO) {
			return ERRORTICK_INFO;
		}
		return 0;
	}

	private int getPackageErrorTicksFromMarkers(IPackageFragment pack) throws CoreException {
		// Packages are special: They must not consider markers on subpackages.
		
		IResource res= pack.getResource();
		if (res == null || !res.isAccessible()) {
			return 0;
		}
		
		// markers on package itself (e.g. missing @NonNullByDefault)
		int severity= res.findMaxProblemSeverity(IMarker.PROBLEM, true, IResource.DEPTH_ZERO);
		if (severity == IMarker.SEVERITY_ERROR)
			return ERRORTICK_ERROR;
		
		// markers on CUs
		for (ICompilationUnit cu : pack.getCompilationUnits()) {
			severity= Math.max(severity, cu.getResource().findMaxProblemSeverity(IMarker.PROBLEM, true, IResource.DEPTH_ZERO));
			if (severity == IMarker.SEVERITY_ERROR)
				return ERRORTICK_ERROR;
		}
		
		// markers on files and folders
		for (Object object : pack.getNonJavaResources()) {
			if (object instanceof IResource) {
				IResource resource= (IResource) object;
				severity= Math.max(severity, resource.findMaxProblemSeverity(IMarker.PROBLEM, true, IResource.DEPTH_INFINITE));
				if (severity == IMarker.SEVERITY_ERROR)
					return ERRORTICK_ERROR;
			}
		}
		
		// SEVERITY_ERROR already handled above
		if (severity == IMarker.SEVERITY_WARNING) {
			return ERRORTICK_WARNING;
		}
		if (severity == IMarker.SEVERITY_INFO) {
			return ERRORTICK_INFO;
		}
		return 0;
	}

	private boolean isMarkerInRange(IMarker marker, ISourceReference sourceElement) throws CoreException {
		if (marker.isSubtypeOf(IMarker.TEXT)) {
			int pos= marker.getAttribute(IMarker.CHAR_START, -1);
			return isInside(pos, sourceElement);
		}
		return false;
	}

	private IAnnotationModel isInJavaAnnotationModel(ICompilationUnit original) {
		if (original.isWorkingCopy()) {
			FileEditorInput editorInput= new FileEditorInput((IFile) original.getResource());
			return JavaPlugin.getDefault().getCompilationUnitDocumentProvider().getAnnotationModel(editorInput);
		}
		return null;
	}


	private int getErrorTicksFromAnnotationModel(IAnnotationModel model, ISourceReference sourceElement) throws CoreException {
		int info= 0;
		Iterator<Annotation> iter= model.getAnnotationIterator();
		while ((info != ERRORTICK_ERROR) && iter.hasNext()) {
			Annotation annot= iter.next();
			IMarker marker= isAnnotationInRange(model, annot, sourceElement);
			if (marker != null) {
				int priority= marker.getAttribute(IMarker.SEVERITY, -1);
				if (priority == IMarker.SEVERITY_INFO) {
					info= ERRORTICK_INFO;
				} else if (priority == IMarker.SEVERITY_WARNING) {
					info= ERRORTICK_WARNING;
				} else if (priority == IMarker.SEVERITY_ERROR) {
					info= ERRORTICK_ERROR;
				}
			}
		}
		return info;
	}

	private IMarker isAnnotationInRange(IAnnotationModel model, Annotation annot, ISourceReference sourceElement) throws CoreException {
		if (annot instanceof MarkerAnnotation) {
			if (sourceElement == null || isInside(model.getPosition(annot), sourceElement)) {
				IMarker marker= ((MarkerAnnotation) annot).getMarker();
				if (marker.exists() && marker.isSubtypeOf(IMarker.PROBLEM)) {
					return marker;
				}
			}
		}
		return null;
	}

	private boolean isInside(Position pos, ISourceReference sourceElement) throws CoreException {
		return pos != null && isInside(pos.getOffset(), sourceElement);
	}

	/**
	 * Tests if a position is inside the source range of an element.
	 * @param pos Position to be tested.
	 * @param sourceElement Source element (must be a IJavaElement)
	 * @return boolean Return <code>true</code> if position is located inside the source element.
	 * @throws CoreException Exception thrown if element range could not be accessed.
	 *
	 * @since 2.1
	 */
	protected boolean isInside(int pos, ISourceReference sourceElement) throws CoreException {
		if (fCachedRange == null) {
			fCachedRange= sourceElement.getSourceRange();
		}
		ISourceRange range= fCachedRange;
		if (range != null) {
			int rangeOffset= range.getOffset();
			return (rangeOffset <= pos && rangeOffset + range.getLength() > pos);
		}
		return false;
	}

	@Override
	public void dispose() {
		if (fProblemChangedListener != null) {
			JavaPlugin.getDefault().getProblemMarkerManager().removeListener(fProblemChangedListener);
			fProblemChangedListener= null;
		}
		if (fRegistry != null && fUseNewRegistry) {
			fRegistry.dispose();
		}
	}

	@Override
	public boolean isLabelProperty(Object element, String property) {
		return true;
	}

	@Override
	public void addListener(ILabelProviderListener listener) {
		if (fListeners == null) {
			fListeners= new ListenerList<>();
		}
		fListeners.add(listener);
		if (fProblemChangedListener == null) {
			fProblemChangedListener= new IProblemChangedListener() {
				@Override
				public void problemsChanged(IResource[] changedResources, boolean isMarkerChange) {
					fireProblemsChanged(changedResources, isMarkerChange);
				}
			};
			JavaPlugin.getDefault().getProblemMarkerManager().addListener(fProblemChangedListener);
		}
	}

	@Override
	public void removeListener(ILabelProviderListener listener) {
		if (fListeners != null) {
			fListeners.remove(listener);
			if (fListeners.isEmpty() && fProblemChangedListener != null) {
				JavaPlugin.getDefault().getProblemMarkerManager().removeListener(fProblemChangedListener);
				fProblemChangedListener= null;
			}
		}
	}

	private void fireProblemsChanged(IResource[] changedResources, boolean isMarkerChange) {
		if (fListeners != null && !fListeners.isEmpty()) {
			LabelProviderChangedEvent event= new ProblemsLabelChangedEvent(this, changedResources, isMarkerChange);
			for (ILabelProviderListener listener : fListeners) {
				listener.labelProviderChanged(event);
			}
		}
	}

	@Override
	public void decorate(Object element, IDecoration decoration) {
		int adornmentFlags= computeAdornmentFlags(element);
		if (adornmentFlags == ERRORTICK_ERROR) {
			decoration.addOverlay(JavaPluginImages.DESC_OVR_ERROR);
		} else if (adornmentFlags == ERRORTICK_BUILDPATH_ERROR) {
			decoration.addOverlay(JavaPluginImages.DESC_OVR_BUILDPATH_ERROR);
		} else if (adornmentFlags == ERRORTICK_WARNING) {
			decoration.addOverlay(JavaPluginImages.DESC_OVR_WARNING);
		} else if (adornmentFlags == ERRORTICK_INFO) {
			decoration.addOverlay(JavaPluginImages.DESC_OVR_INFO);
		}
	}

}
