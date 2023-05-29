/*
 * Copyright (C) 2010-2023 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.ninja.action.upgrade.step;

import java.io.File;
import java.io.IOException;

import com.evolveum.midpoint.ninja.action.upgrade.UpgradeOptions;
import com.evolveum.midpoint.ninja.action.upgrade.UpgradeStepsContext;

import com.evolveum.midpoint.ninja.opts.ConnectionOptions;

import org.apache.commons.io.FileUtils;

import com.evolveum.midpoint.ninja.action.upgrade.StepResult;
import com.evolveum.midpoint.ninja.action.upgrade.UpgradeStep;

import org.apache.commons.lang3.BooleanUtils;
import org.jetbrains.annotations.NotNull;

public class UpgradeMidpointHomeStep implements UpgradeStep<StepResult> {

    private static final String VAR_DIRECTORY = "var";

    private final UpgradeStepsContext context;

    public UpgradeMidpointHomeStep(@NotNull UpgradeStepsContext context) {
        this.context = context;
    }

    @Override
    public String getIdentifier() {
        return "upgradeMidpointHome";
    }

    public StepResult execute() throws Exception {
        final ConnectionOptions connectionOptions = context.getContext().getOptions(ConnectionOptions.class);
        final File midpointHomeDirectory = new File(connectionOptions.getMidpointHome());

        final DownloadDistributionResult distributionResult = context.getResult(DownloadDistributionResult.class);
        final File distributionDirectory = distributionResult.getDistributionDirectory();

        final UpgradeOptions upgradeOptions = context.getOptions();
        final boolean backupFiles = BooleanUtils.isTrue(upgradeOptions.isBackupMidpointDirectory());

        File backupDirectory = null;
        if (backupFiles) {
            backupDirectory = new File(midpointHomeDirectory, ".backup-" + System.currentTimeMillis());
            backupDirectory.mkdir();
        }

        for (File file : distributionDirectory.listFiles()) {
            String fileName = file.getName();

            if (backupFiles) {
                File newFile = new File(midpointHomeDirectory, fileName);

                if (!VAR_DIRECTORY.equals(fileName)) {
                    if (newFile.exists()) {
                        FileUtils.moveToDirectory(newFile, backupDirectory, false);
                    }
                } else {
                    // don't back up var directory, upgrade shouldn't touch it, back up only content if needed
                    FileUtils.forceMkdir(new File(backupDirectory, fileName));
                }
            }

            if (VAR_DIRECTORY.equals(fileName)) {
                copyFiles(file, new File(midpointHomeDirectory, fileName), new File(backupDirectory, fileName), backupFiles);
            } else {
                FileUtils.moveToDirectory(file, midpointHomeDirectory, false);
            }
        }

        return new StepResult() {
        };
    }

    private void copyFiles(File srcDir, File dstDir, File backupDir, boolean backup) throws IOException {
        File[] files = srcDir.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            String fileName = file.getName();

            if (backup) {
                File newFile = new File(dstDir, fileName);
                if (newFile.exists()) {
                    FileUtils.moveToDirectory(newFile, backupDir, false);
                }
            }

            FileUtils.moveToDirectory(file, dstDir, false);
        }
    }
}
