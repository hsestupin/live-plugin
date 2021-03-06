package liveplugin.toolwindow.settingsmenu.languages;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.Pair;
import liveplugin.toolwindow.util.DependenciesUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.openapi.roots.OrderRootType.CLASSES;
import static com.intellij.openapi.util.Pair.create;
import static com.intellij.util.containers.ContainerUtil.map;
import static liveplugin.LivePluginAppComponent.LIVEPLUGIN_LIBS_PATH;
import static liveplugin.LivePluginAppComponent.clojureIsOnClassPath;
import static liveplugin.MyFileUtil.fileNamesMatching;

public class AddClojureLibsAsDependency extends AnAction implements DumbAware {
	private static final String LIBRARY_NAME = "LivePlugin - Clojure";

	@Override public void actionPerformed(@NotNull AnActionEvent event) {
		Project project = event.getProject();
		if (project == null) return;

		if (DependenciesUtil.anyModuleHasLibraryAsDependencyIn(project, LIBRARY_NAME)) {
			DependenciesUtil.removeLibraryDependencyFrom(project, LIBRARY_NAME);
		} else {
			List<Pair<String, OrderRootType>> paths = map(fileNamesMatching(DownloadClojureLibs.LIB_FILES_PATTERN, LIVEPLUGIN_LIBS_PATH), fileName -> create("jar://" + LIVEPLUGIN_LIBS_PATH + fileName + "!/", CLASSES));
			DependenciesUtil.addLibraryDependencyTo(project, LIBRARY_NAME, paths);
		}
	}

	@Override public void update(@NotNull AnActionEvent event) {
		Project project = event.getProject();
		if (project == null) return;

		if (DependenciesUtil.anyModuleHasLibraryAsDependencyIn(project, LIBRARY_NAME)) {
			event.getPresentation().setText("Remove Clojure Libraries from Project");
			event.getPresentation().setDescription("Remove Clojure Libraries from Project");
		} else {
			event.getPresentation().setText("Add Clojure Libraries to Project");
			event.getPresentation().setDescription("Add Clojure Libraries to Project");
			event.getPresentation().setEnabled(clojureIsOnClassPath());
		}
	}
}
