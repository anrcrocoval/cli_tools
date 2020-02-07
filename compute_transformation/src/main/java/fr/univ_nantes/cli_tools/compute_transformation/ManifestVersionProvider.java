package fr.univ_nantes.cli_tools.compute_transformation;

import picocli.CommandLine.IVersionProvider;

import java.io.IOException;
import java.net.URL;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

public class ManifestVersionProvider implements IVersionProvider {

    @Override
    public String[] getVersion() {
        return new String[] { getVersionString() };
    }

    private String getVersionString() {
        String className = this.getClass().getSimpleName() + ".class";
        String classPath = this.getClass().getResource(className).toString();
        String manifestPath = classPath.substring(0, classPath.lastIndexOf("!") + 1) + "/META-INF/MANIFEST.MF";
        Manifest manifest = null;
        try {
            manifest = new Manifest(new URL(manifestPath).openStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
        Attributes attributes = manifest.getMainAttributes();
        return String.format("%s",
            attributes.getValue("Implementation-Version")
        );
    }
}
