/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.corext.refactoring.surround;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.VariableDeclaration;

import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.Selection;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.internal.corext.refactoring.base.JavaSourceContext;
import org.eclipse.jdt.internal.corext.refactoring.util.CodeAnalyzer;

public class SurroundWithTryCatchAnalyzer extends CodeAnalyzer {

	private ITypeBinding[] fExceptions;
	private VariableDeclaration[] fLocals;

	public SurroundWithTryCatchAnalyzer(ICompilationUnit unit, Selection selection) throws JavaModelException {
		super(unit, selection, false);
	}
	
	public ITypeBinding[] getExceptions() {
		return fExceptions;
	}
	
	public VariableDeclaration[] getAffectedLocals() {
		return fLocals;
	}
	
	public void endVisit(CompilationUnit node) {
		superCall: {
			if (getStatus().hasFatalError())
				break superCall;
			if (!hasSelectedNodes()) {
				invalidSelection(RefactoringCoreMessages.getString("SurroundWithTryCatchAnalyzer.doesNotCover")); //$NON-NLS-1$
				break superCall;
			}
			MethodDeclaration enclosingMethod= (MethodDeclaration)ASTNodes.getParent(getFirstSelectedNode(), MethodDeclaration.class);
			if (enclosingMethod == null) {
				invalidSelection(RefactoringCoreMessages.getString("SurroundWithTryCatchAnalyzer.doesNotContain"));  //$NON-NLS-1$
				break superCall;
			}
			fExceptions= ExceptionAnalyzer.perform(enclosingMethod, getSelection());
			if (fExceptions == null || fExceptions.length == 0) {
				invalidSelection(RefactoringCoreMessages.getString("SurroundWithTryCatchAnalyzer.noUncaughtExceptions")); //$NON-NLS-1$
				break superCall;
			}
			if (!onlyStatements()) {
				invalidSelection(RefactoringCoreMessages.getString("SurroundWithTryCatchAnalyzer.onlyStatements")); //$NON-NLS-1$
			}
			fLocals= LocalDeclarationAnalyzer.perform(enclosingMethod, getSelection());
		}
		super.endVisit(node);
	}
	
	public void endVisit(SuperConstructorInvocation node) {
		if (getSelection().getEndVisitSelectionMode(node) == Selection.SELECTED) {
			invalidSelection(RefactoringCoreMessages.getString("SurroundWithTryCatchAnalyzer.cannotHandleSuper"), JavaSourceContext.create(fCUnit, node)); //$NON-NLS-1$
		}
		super.endVisit(node);
	}
	
	private boolean onlyStatements() {
		ASTNode[] nodes= getSelectedNodes();
		for (int i= 0; i < nodes.length; i++) {
			if (!(nodes[i] instanceof Statement))
				return false;
		}
		return true;
	}	
}
