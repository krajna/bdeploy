package io.bdeploy.bhive.meta;

import java.io.InputStream;
import java.util.Map.Entry;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHiveExecution;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.model.Tree;
import io.bdeploy.bhive.model.Tree.EntryType;
import io.bdeploy.bhive.op.ImportObjectOperation;
import io.bdeploy.bhive.op.InsertArtificialTreeOperation;
import io.bdeploy.bhive.op.InsertManifestOperation;
import io.bdeploy.bhive.op.ManifestDeleteOldByIdOperation;
import io.bdeploy.bhive.op.ManifestLoadOperation;
import io.bdeploy.bhive.op.ManifestMaxIdOperation;
import io.bdeploy.bhive.op.TreeEntryLoadOperation;
import io.bdeploy.bhive.op.TreeLoadOperation;
import io.bdeploy.bhive.util.StorageHelper;

/**
 * A {@link MetaManifest} allows to persist an load metadata associated with a given {@link Manifest}.
 * <p>
 * It is possible to put multiple metadata infos on a single {@link Manifest}, distinguished by the {@link Class}
 * {@link Class#getSimpleName() simple name}. Each metadata object is stored in a separate blob to allow cheap reading of
 * individual data streams without the need to touch/read any other metadata object.
 */
public class MetaManifest<T> {

    private static final Logger log = LoggerFactory.getLogger(MetaManifest.class);

    private static final String META_PREFIX = "._meta/";
    private static final int META_HIST_SIZE = 2;

    private final Key parent;
    private final String metaName;
    private final Class<T> metaClazz;

    /**
     * Create a {@link MetaManifest} for the given {@link Key} which identifies the {@link Manifest} to "annotate" with metadata.
     *
     * @param parent the parent {@link Manifest}s {@link Key}.
     * @param useParentTag whether the metadata is tag specific or valid for any tag of the given {@link Manifest}. Attention:
     *            reading and writing must happen with the same parameter. Tag specific metadata is not read by a non-tag specific
     *            {@link MetaManifest} and vice versa.
     * @param metaClazz the {@link Class} of the metadata to store. This {@link Class} must be serializable by the
     *            {@link StorageHelper}.
     */
    public MetaManifest(Manifest.Key parent, boolean useParentTag, Class<T> metaClazz) {
        this.parent = parent;
        this.metaName = META_PREFIX + parent.getName() + (useParentTag ? ("/" + parent.getTag()) : "");
        this.metaClazz = metaClazz;
    }

    /**
     * Read the current metadata from the given {@link BHiveExecution}.
     *
     * @param source the source {@link BHiveExecution}
     * @return The current version of the metadata. <code>null</code> if no metadata for the given {@link Class} is present.
     */
    public T read(BHiveExecution source) {
        Optional<Long> id = source.execute(new ManifestMaxIdOperation().setManifestName(metaName));
        if (!id.isPresent()) {
            return null;
        }

        Manifest.Key key = new Manifest.Key(metaName, id.get().toString());
        Manifest mf = source.execute(new ManifestLoadOperation().setManifest(key));

        try (InputStream is = source
                .execute(new TreeEntryLoadOperation().setRootTree(mf.getRoot()).setRelativePath(metaFileName()))) {
            return StorageHelper.fromStream(is, metaClazz);
        } catch (Exception e) {
            log.debug("Cannot read " + metaFileName() + " for " + parent, e);
            return null; // this is "OK", since an exception is thrown if the object not yet exists.
        }
    }

    /**
     * Writes the given metadata object to the given {@link BHiveExecution}.
     *
     * @param target the target {@link BHiveExecution}
     * @param meta the metadata object. <code>null</code> will cause the entry to be deleted, so subsequent {@link #read} calls
     *            will return <code>null</code> as well.
     * @return the {@link Key} of the current metadata manifest.
     */
    public Manifest.Key write(BHiveExecution target, T meta) {
        String targetTag = "1";
        Optional<Long> id = target.execute(new ManifestMaxIdOperation().setManifestName(metaName));
        Tree oldTree = null;

        if (id.isPresent()) {
            targetTag = Long.toString(id.get() + 1);

            // read existing version if it is present
            Manifest mf = target
                    .execute(new ManifestLoadOperation().setManifest(new Manifest.Key(metaName, id.get().toString())));
            oldTree = target.execute(new TreeLoadOperation().setTree(mf.getRoot()));
        }

        Manifest.Key targetKey = new Manifest.Key(metaName, targetTag);
        Manifest.Builder newMf = new Manifest.Builder(targetKey);
        Tree.Builder newTree = new Tree.Builder();

        // add the new metadata object
        String metaFileName = metaFileName();
        if (meta != null) {
            // if null, we don't write the entry -> delete.
            ObjectId oid = target.execute(new ImportObjectOperation().setData(StorageHelper.toRawBytes(meta)));
            newTree.add(new Tree.Key(metaFileName, EntryType.BLOB), oid);
        }

        // now copy other entries to the new tree
        if (oldTree != null) {
            for (Entry<Tree.Key, ObjectId> entry : oldTree.getChildren().entrySet()) {
                if (entry.getKey().getName().equals(metaFileName)) {
                    continue; // we updated this one.
                }

                newTree.add(entry.getKey(), entry.getValue());
            }
        }

        // insert tree and manifest
        ObjectId newTreeId = target.execute(new InsertArtificialTreeOperation().setTree(newTree));
        target.execute(new InsertManifestOperation().addManifest(newMf.setRoot(newTreeId).build(target)));
        target.execute(new ManifestDeleteOldByIdOperation().setAmountToKeep(META_HIST_SIZE).setToDelete(metaName));

        return targetKey;
    }

    /**
     * Removes the metadata from the given {@link BHiveExecution}. Convenience shortcut for {@link #write(BHiveExecution, Object)
     * write(target, null)}.
     *
     * @param target the target to delete from.
     * @return the {@link Key} of the current metadata manifest.
     */
    public Manifest.Key remove(BHiveExecution target) {
        return write(target, null);
    }

    private String metaFileName() {
        return metaClazz.getSimpleName() + ".json";
    }

}