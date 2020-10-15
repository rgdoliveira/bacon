package org.jboss.pnc.bacon.pig.impl.addons.rhba;

import org.jboss.pnc.bacon.pig.impl.addons.AddOn;
import org.jboss.pnc.bacon.pig.impl.config.PigConfiguration;
import org.jboss.pnc.bacon.pig.impl.pnc.ArtifactWrapper;
import org.jboss.pnc.bacon.pig.impl.pnc.BuildInfoCollector;
import org.jboss.pnc.bacon.pig.impl.pnc.PncBuild;
import org.jboss.pnc.bacon.pig.impl.utils.GAV;
import org.jboss.pnc.enums.RepositoryType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class OfflineManifestGenerator extends AddOn {

    private static final String ADDON_NAME = "offlineManifestGenerator";

    private static final Logger log = LoggerFactory.getLogger(OfflineManifestGenerator.class);

    public OfflineManifestGenerator(
            PigConfiguration pigConfiguration,
            Map<String, PncBuild> builds,
            String releasePath,
            String extrasPath) {
        super(pigConfiguration, builds, releasePath, extrasPath);
    }

    public String getName() {
        return ADDON_NAME;
    }

    public void trigger() {
        log.info("Will generate the offline manifest");
        BuildInfoCollector buildInfoCollector = new BuildInfoCollector();
        List<ArtifactWrapper> artifactsToListRaw = new ArrayList<>();
        for (PncBuild build : builds.values()) {
            artifactsToListRaw.addAll(build.getBuiltArtifacts());
            // TODO: Add filter, basing on the targetRepository.repositoryType, when NCL-6079 is done
            buildInfoCollector.addDependencies(build, "");
            artifactsToListRaw.addAll(build.getDependencyArtifacts());
            log.debug("Dependencies for build {}: {}", build.getId(), build.getDependencyArtifacts().size());
        }
        log.debug("Number of collected artifacts for the Offline manifest: {}", artifactsToListRaw.size());

        List<String> exclusions = pigConfiguration.getFlow().getRepositoryGeneration().getExcludeArtifacts();
        artifactsToListRaw.removeIf(artifact -> {
            for (String exclusion : exclusions) {
                if (Pattern.matches(exclusion, artifact.getGapv())) {
                    return true;
                }
            }
            return false;
        });

        log.debug("Number of collected artifacts after exclusion: {}", artifactsToListRaw.size());
        // Map<String, ArtifactWrapper> artifactsToList = new HashMap<>();
        // for (ArtifactWrapper artifact : artifactsToListRaw) {
        // artifactsToList.put(artifact.getGapv(), artifact);
        // }

        List<ArtifactWrapper> artifactsToList = artifactsToListRaw.stream().distinct().collect(Collectors.toList());

        log.info("Number of collected artifacts without duplicates: {}", artifactsToList.size());

        try (PrintWriter file = new PrintWriter(releasePath + "offliner.txt", StandardCharsets.UTF_8.name())) {
            for (ArtifactWrapper artifact : artifactsToList) {
                // TODO: Remove the check, when NCL-6079 is done
                if (artifact.getRepositoryType() == RepositoryType.MAVEN) {
                    GAV gav = artifact.toGAV();
                    String offlinerString = String
                            .format("%s,%s/%s", artifact.getSha256(), gav.toVersionPath(), gav.toFileName());
                    file.println(offlinerString);
                }

            }

            List<GAV> extraGavs = pigConfiguration.getFlow()
                    .getRepositoryGeneration()
                    .getExternalAdditionalArtifacts()
                    .stream()
                    .map(GAV::fromColonSeparatedGAPV)
                    .collect(Collectors.toList());
            for (GAV extraGav : extraGavs) {
                String offlinerString = String.format("%s/%s", extraGav.toVersionPath(), extraGav.toFileName());
                file.println(offlinerString);
            }

        } catch (FileNotFoundException | UnsupportedEncodingException e) {
            log.error("Error generating the Offline manifest", e);
        }

    }

}