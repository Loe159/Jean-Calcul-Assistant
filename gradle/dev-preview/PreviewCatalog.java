// Runs in a separate JVM so the Kotlin parser uses a single compiler classpath.
import com.google.gson.Gson;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import org.jetbrains.kotlin.cli.FrontendConfigurationKeysKt;
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles;
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment;
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer;
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar;
import org.jetbrains.kotlin.config.CompilerConfiguration;
import org.jetbrains.kotlin.fileClasses.JvmFileClassUtil;
import org.jetbrains.kotlin.psi.KtNamedFunction;
import org.jetbrains.kotlin.psi.KtPsiFactory;

class PreviewCatalog {
    private record Entry(String fqn, String file, String name) {}

    public static void main(String[] args) throws Exception {
        var root = Path.of(args[0]);
        var output = Path.of(args[1]);
        var disposable = Disposer.newDisposable();
        try {
            var configuration = new CompilerConfiguration();
            FrontendConfigurationKeysKt.setExtensionsStorage(configuration, new CompilerPluginRegistrar.ExtensionStorage());
            var environment = KotlinCoreEnvironment.Companion.createForProduction(
                disposable, configuration, EnvironmentConfigFiles.JVM_CONFIG_FILES);
            var parser = new KtPsiFactory(environment.getProject(), false);
            var entries = new ArrayList<Entry>();
            try (var paths = Files.walk(root)) {
                for (var path : paths.filter(Files::isRegularFile)
                        .filter(file -> file.toString().endsWith(".kt")).sorted().toList()) {
                    var file = parser.createFile(path.getFileName().toString(), Files.readString(path));
                    var className = JvmFileClassUtil.getFileClassInfoNoResolve(file).getFileClassFqName().asString();
                    for (var declaration : file.getDeclarations()) {
                        if (declaration instanceof KtNamedFunction function && function.getName() != null
                                && function.getAnnotationEntries().stream().anyMatch(annotation -> {
                                    var name = annotation.getShortName();
                                    return name != null && (name.asString().endsWith("Preview")
                                        || name.asString().endsWith("Previews"));
                                })) {
                            entries.add(new Entry(className + "." + function.getName(),
                                root.relativize(path).toString().replace('\\', '/'), function.getName()));
                        }
                    }
                }
            }
            Files.writeString(output, new Gson().toJson(entries));
        } finally {
            Disposer.dispose(disposable);
        }
    }
}
