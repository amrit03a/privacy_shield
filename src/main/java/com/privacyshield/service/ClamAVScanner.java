
package com.privacyshield.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@Service
public class ClamAVScanner {

    private static final String SCANNER =
            "C:\\Program Files\\ClamAV\\clamscan.exe";

    private static final String DATABASE =
            "C:\\Program Files\\ClamAV\\database";

    public boolean isClean(Path file)
            throws IOException, InterruptedException {

        if (file == null || !Files.isRegularFile(file)) {
            throw new IOException("File does not exist.");
        }

        ProcessBuilder builder = new ProcessBuilder(
                SCANNER,
                "--database=" + DATABASE,
                "--no-summary",
                file.toAbsolutePath().toString()
        );

        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);

        Process process = builder.start();

        if (!process.waitFor(90, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            process.waitFor();
            throw new IOException("ClamAV scan timed out.");
        }

        int exitCode = process.exitValue();

        if (exitCode == 0) {
            return true;
        }

        if (exitCode == 1) {
            return false;
        }

        throw new IOException(
                "ClamAV scan failed with exit code: " + exitCode
        );
    }
}
