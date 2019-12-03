package io.bdeploy.ui.dto;

import java.util.Collection;
import java.util.Collections;

import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.common.Version;

/**
 * Stores the required manifest keys that must be transfered and installed on the target.
 */
public class MinionUpdateDto {

    /**
     * The version that will be installed
     */
    public Version updateVersion;

    /**
     * Flag whether or not updates can be installed.
     */
    public boolean updateAvailable;

    /**
     * The packages to install
     */
    public Collection<Key> packagesToInstall = Collections.emptyList();

    /**
     * The missing packages that must be transfered
     */
    public Collection<Key> packagesToTransfer = Collections.emptyList();

}