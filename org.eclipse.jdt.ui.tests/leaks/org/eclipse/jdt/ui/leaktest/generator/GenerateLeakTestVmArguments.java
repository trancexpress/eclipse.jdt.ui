package org.eclipse.jdt.ui.leaktest.generator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class GenerateLeakTestVmArguments {

	private static final String[] additionalVmArguments = {
			"--add-modules ALL-SYSTEM",
			"--add-opens java.base/jdk.internal.icu.impl.data.icudt67b=ALL-UNNAMED",
			"--add-opens java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED",
			"--add-opens java.base/jdk.internal.org.objectweb.asm.commons=ALL-UNNAMED",
			"--add-opens java.base/jdk.internal.org.objectweb.asm.signature=ALL-UNNAMED",
			"--add-opens java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED",
			"--add-opens java.base/jdk.internal.org.objectweb.asm.tree.analysis=ALL-UNNAMED",
			"--add-opens java.base/jdk.internal.org.objectweb.asm.util=ALL-UNNAMED",
			"--add-opens java.base/jdk.internal.util.jar=ALL-UNNAMED",
			"--add-opens java.base/sun.invoke=ALL-UNNAMED",
			"--add-opens java.base/sun.io=ALL-UNNAMED",
			"--add-opens java.base/sun.security.action=ALL-UNNAMED",
	};

	public static void main(String[] args) throws IOException {
		Path output = Paths.get(args[0]);
		List<String> vmArguments = new ArrayList<>();
		vmArguments.addAll(Arrays.asList(additionalVmArguments));
		Set<Module> modules = ModuleLayer.boot().modules();
		for (Module module : modules) {
			Set<String> packages = module.getPackages();
			for (String eachPackage : packages) {
				vmArguments.add("--add-opens " + module.getName() + "/" + eachPackage + "=ALL-UNNAMED");
			}
		}
		Files.writeString(output, "javavmargs=" + String.join(" \\" + System.lineSeparator(), vmArguments));
	}
}
