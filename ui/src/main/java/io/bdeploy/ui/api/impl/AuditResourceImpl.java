package io.bdeploy.ui.api.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
import io.bdeploy.common.util.JacksonHelper;
import io.bdeploy.jersey.audit.RollingFileAuditor;
import io.bdeploy.ui.api.AuditResource;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.dto.AuditLogDto;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.SecurityContext;

public class AuditResourceImpl implements AuditResource {

    private static final Logger log = LoggerFactory.getLogger(AuditResourceImpl.class);

    @Inject
    private BHiveRegistry registry;

    @Inject
    private Minion minion;

    @Context
    private SecurityContext context;

    @Override
    public List<AuditLogDto> hiveAuditLog(String hiveParam, long lastInstant, int lineLimit) {
        log.debug("hiveAuditLog({},{},{})", hiveParam, lastInstant, lineLimit);
        BHive hive = registry.get(hiveParam);
        if (hive != null && hive.getAuditor() instanceof RollingFileAuditor) {
            return scanFiles((RollingFileAuditor) hive.getAuditor(), lastInstant, lineLimit);
        } else {
            throw new WebApplicationException("Cannot read auditor of hive " + hiveParam);
        }
    }

    private List<AuditLogDto> scanFiles(RollingFileAuditor auditor, long lastInstant, int lineLimit) {
        log.debug("scanFiles(<auditor>,{},{})", lastInstant, lineLimit);
        List<AuditLogDto> result = new ArrayList<>();
        Path currentPath = auditor.getJsonFile();

        // scan current log
        boolean readMore = false;
        try (InputStream is = Files.newInputStream(currentPath)) {
            readMore = scanFile(result, is, lastInstant, lineLimit);
        } catch (IOException e) {
            log.error("Failed to open {}", currentPath, e);
        }

        // scan archived logs
        Path[] backupFiles = auditor.getJsonBackups();
        for (int i = 0; readMore && i < backupFiles.length; i++) {
            currentPath = backupFiles[i];
            try (InputStream gis = new GZIPInputStream(Files.newInputStream(currentPath))) {
                readMore = scanFile(result, gis, lastInstant, lineLimit);
            } catch (IOException e) {
                log.error("Failed to open {}", currentPath, e);
            }

        }
        return result;
    }

    private boolean scanFile(List<AuditLogDto> result, InputStream stream, long lastInstant, int limit) throws IOException {
        ObjectMapper mapper = JacksonHelper.createDefaultObjectMapper();
        List<AuditLogDto> chunk = new ArrayList<>();
        BufferedReader br = new BufferedReader(new InputStreamReader(stream));

        String line;
        boolean first = true;
        while ((line = br.readLine()) != null) {
            AuditLogDto dto;
            try {
                dto = mapper.readValue(line, AuditLogDto.class);
            } catch (IOException ioe) {
                dto = new AuditLogDto(line);
            }

            if (first) {
                first = false;
                // check if the whole file is out of range (limited to records that are older than the first one)
                // if yes, stop reading here
                if (lastInstant > 0 && lastInstant < dto.instant.toEpochMilli()) {
                    return true;
                }
            }

            if ((lastInstant == 0 || lastInstant > dto.instant.toEpochMilli())) {
                chunk.add(dto);
            }
        }
        int required = Math.max(0, limit - result.size());
        if (required >= chunk.size()) {
            result.addAll(0, chunk);
        } else {
            // take all records with the same instant value (and exceed the limit)
            int idx = chunk.size() - required;
            while (idx > 0 && chunk.get(idx).instant.toEpochMilli() == chunk.get(idx - 1).instant.toEpochMilli()) {
                idx--;
            }
            result.addAll(0, chunk.subList(idx, chunk.size()));
        }
        return result.size() < limit;
    }

}
