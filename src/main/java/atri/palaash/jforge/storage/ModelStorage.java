package atri.palaash.jforge.storage;

import atri.palaash.jforge.model.ModelDescriptor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Manages the local file-system storage for downloaded AI models.
 * <p>
 * Think of this as a filing cabinet for models. It keeps track of
 * the root directory where models are stored and provides simple
 * methods to check if a model is available, get its path, and ensure
 * its parent directory exists before downloading.
 */
public class ModelStorage {

    /** The root directory under which all model files are stored. */
    private final Path modelRoot;

    /**
     * Creates storage rooted at the default location: {@code ~/.jforge-models/}.
     */
    public ModelStorage() {
        this(Paths.get(System.getProperty("user.home"), ".jforge-models"));
    }

    /**
     * Creates storage at a custom root directory.
     *
     * @param modelRoot the top-level directory for model storage
     */
    public ModelStorage(Path modelRoot) {
        this.modelRoot = modelRoot;
    }

    /**
     * Returns the root directory where models are stored.
     *
     * @return the model root {@link Path}
     */
    public Path root() {
        return modelRoot;
    }

    /**
     * Resolves the expected file-system path for a given model descriptor.
     *
     * @param descriptor the model descriptor
     * @return the full path where the model file would be located
     */
    public Path modelPath(ModelDescriptor descriptor) {
        return modelRoot.resolve(descriptor.relativePath());
    }

    /**
     * Checks whether the model described by {@code descriptor} already
     * exists on disk as a regular file.
     *
     * @param descriptor the model descriptor
     * @return {@code true} if the model file exists and is a regular file
     */
    public boolean isAvailable(ModelDescriptor descriptor) {
        Path file = modelPath(descriptor);
        return Files.exists(file) && Files.isRegularFile(file);
    }

    /**
     * Creates the parent directory of the model file if it does not
     * already exist. Must be called before writing a new model file.
     *
     * @param descriptor the model descriptor
     * @throws IOException if the directory cannot be created
     */
    public void ensureParentDirectory(ModelDescriptor descriptor) throws IOException {
        Path target = modelPath(descriptor);
        Files.createDirectories(target.getParent());
    }
}
