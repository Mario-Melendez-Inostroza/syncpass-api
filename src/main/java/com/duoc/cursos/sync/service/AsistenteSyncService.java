package com.duoc.cursos.sync.service;

import com.duoc.cursos.model.Asistente;
import com.duoc.cursos.model.enums.EstadoInscripcion;
import com.duoc.cursos.repository.AsistenteRepository;
import com.duoc.cursos.sync.dto.AsistenteSyncItem;
import com.duoc.cursos.sync.dto.LoteAsistentesResponse;
import com.duoc.cursos.sync.dto.ResultadoAsistenteItem;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Alta de asistentes en terreno (caso §3.1), por lotes e idempotente.
 *
 * Mismas reglas que {@link SyncService}: cada ítem es independiente y corre en su propia transacción, y el uuid
 * que genera el teléfono es la clave de idempotencia (reenviar un lote es inocuo).
 *
 * El servidor decide lo que el cliente no puede decidir: el estado de inscripción (siempre SIN_INSCRIPCION,
 * porque son quienes llegan sin inscripción previa) y el código sintético.
 */
@Service
public class AsistenteSyncService {

    private static final Logger log = LoggerFactory.getLogger(AsistenteSyncService.class);

    static final String PREFIJO_CODIGO = "TER-";
    private static final int HEX_DEL_CODIGO = 12;
    private static final int UUID_MAX = 36;

    private final AsistenteRepository asistenteRepository;
    private final Validator validator;
    private final TransactionTemplate transaccion;

    public AsistenteSyncService(AsistenteRepository asistenteRepository,
                                Validator validator,
                                PlatformTransactionManager transactionManager) {
        this.asistenteRepository = asistenteRepository;
        this.validator = validator;
        this.transaccion = new TransactionTemplate(transactionManager);
    }

    public LoteAsistentesResponse sincronizar(List<AsistenteSyncItem> items) {
        List<ResultadoAsistenteItem> resultados = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            resultados.add(procesar(i, items.get(i)));
        }
        LoteAsistentesResponse respuesta = LoteAsistentesResponse.de(resultados);
        log.info("Lote de asistentes en terreno: {} aceptados, {} duplicados, {} rechazados",
                respuesta.aceptados(), respuesta.duplicados(), respuesta.rechazados());
        return respuesta;
    }

    private ResultadoAsistenteItem procesar(int indice, AsistenteSyncItem item) {
        if (item == null) {
            return ResultadoAsistenteItem.invalido(indice, null, "registro");
        }
        String camposInvalidos = camposInvalidos(item);
        if (camposInvalidos != null) {
            return ResultadoAsistenteItem.invalido(indice, uuidSeguro(item.getUuid()), camposInvalidos);
        }
        try {
            return transaccion.execute(status -> guardar(indice, item));
        } catch (DataIntegrityViolationException ex) {
            // Un reintento concurrente ganó la carrera por la restricción única: ya existe, es un duplicado.
            return asistenteRepository.findByUuid(item.getUuid())
                    .map(a -> ResultadoAsistenteItem.duplicado(indice, item.getUuid(), a.getId(), a.getCodigo()))
                    .orElseGet(() -> {
                        log.warn("Integridad violada al dar de alta un asistente (indice={}): {}", indice,
                                ex.getClass().getSimpleName());
                        return ResultadoAsistenteItem.invalido(indice, item.getUuid(), "integridad");
                    });
        }
    }

    private ResultadoAsistenteItem guardar(int indice, AsistenteSyncItem item) {
        var existente = asistenteRepository.findByUuid(item.getUuid());
        if (existente.isPresent()) {
            Asistente a = existente.get();
            return ResultadoAsistenteItem.duplicado(indice, item.getUuid(), a.getId(), a.getCodigo());
        }

        Asistente asistente = new Asistente();
        asistente.setUuid(item.getUuid());
        asistente.setCodigo(codigoPara(item.getUuid()));
        asistente.setTipo(item.getTipo());
        asistente.setEstadoInscripcion(EstadoInscripcion.SIN_INSCRIPCION);

        // saveAndFlush: la violación de la restricción única debe saltar dentro de la transacción del ítem.
        Asistente guardado = asistenteRepository.saveAndFlush(asistente);
        return ResultadoAsistenteItem.aceptado(indice, item.getUuid(), guardado.getId(), guardado.getCodigo());
    }

    /** "TER-" + los primeros 12 hexadecimales del uuid, en mayúsculas: 16 caracteres, estable y sin colisiones prácticas. */
    static String codigoPara(String uuid) {
        String hex = uuid.replace("-", "").toUpperCase(Locale.ROOT);
        return PREFIJO_CODIGO + hex.substring(0, Math.min(HEX_DEL_CODIGO, hex.length()));
    }

    private String camposInvalidos(AsistenteSyncItem item) {
        Set<ConstraintViolation<AsistenteSyncItem>> violaciones = validator.validate(item);
        if (violaciones.isEmpty()) {
            return null;
        }
        return violaciones.stream()
                .map(v -> v.getPropertyPath().toString())
                .distinct()
                .sorted()
                .collect(Collectors.joining(","));
    }

    private static String uuidSeguro(String uuid) {
        return uuid != null && uuid.length() <= UUID_MAX ? uuid : null;
    }
}
