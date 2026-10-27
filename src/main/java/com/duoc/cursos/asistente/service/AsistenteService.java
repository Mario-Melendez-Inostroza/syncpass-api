package com.duoc.cursos.asistente.service;

import com.duoc.cursos.asistente.dto.AsistenteAltaRequest;
import com.duoc.cursos.asistente.dto.AsistenteEdicionRequest;
import com.duoc.cursos.asistente.dto.AsistenteFilaDto;
import com.duoc.cursos.exception.ConflictoException;
import com.duoc.cursos.exception.RecursoNoEncontradoException;
import com.duoc.cursos.model.Asistente;
import com.duoc.cursos.model.enums.EstadoInscripcion;
import com.duoc.cursos.repository.AsistenciaRepository;
import com.duoc.cursos.repository.AsistenteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Lista de asistentes que gestiona el Docente / Coordinador(a) (caso §3.1, §3.2). Sin datos personales: cada asistente
 * es un código sintético y un tipo (caso §5.1). El código lo genera el servidor, nunca el cliente.
 */
@Service
public class AsistenteService {

    static final String PREFIJO_CODIGO = "INS-";

    private final AsistenteRepository asistenteRepository;
    private final AsistenciaRepository asistenciaRepository;

    public AsistenteService(AsistenteRepository asistenteRepository, AsistenciaRepository asistenciaRepository) {
        this.asistenteRepository = asistenteRepository;
        this.asistenciaRepository = asistenciaRepository;
    }

    @Transactional(readOnly = true)
    public List<AsistenteFilaDto> listar() {
        return asistenteRepository.listarConAsistencias();
    }

    /**
     * Crea [cantidad] inscritos con el mismo tipo e inscripción. El código es "INS-" + el id con ceros (INS-000123): se
     * conoce después de insertar, así que nace con un código provisional único y se corrige en la misma transacción.
     * El prefijo propio evita chocar con los códigos del catálogo de demostración (DEMO-…) y los de terreno (TER-…).
     */
    @Transactional
    public List<AsistenteFilaDto> crear(AsistenteAltaRequest req) {
        int cantidad = req.getCantidad() != null ? req.getCantidad() : 1;
        EstadoInscripcion inscripcion = req.getEstadoInscripcion() != null ? req.getEstadoInscripcion() : EstadoInscripcion.INSCRITO;

        List<AsistenteFilaDto> creados = new ArrayList<>(cantidad);
        for (int i = 0; i < cantidad; i++) {
            Asistente a = new Asistente();
            a.setCodigo("TMP-" + UUID.randomUUID());
            a.setTipo(req.getTipo());
            a.setEstadoInscripcion(inscripcion);
            a = asistenteRepository.saveAndFlush(a);
            a.setCodigo(String.format("%s%06d", PREFIJO_CODIGO, a.getId()));
            a = asistenteRepository.saveAndFlush(a);
            creados.add(new AsistenteFilaDto(a.getId(), a.getCodigo(), a.getTipo(), a.getEstadoInscripcion(), 0, false));
        }
        return creados;
    }

    @Transactional
    public AsistenteFilaDto actualizar(Long id, AsistenteEdicionRequest req) {
        Asistente a = buscar(id);
        a.setTipo(req.getTipo());
        a.setEstadoInscripcion(req.getEstadoInscripcion());
        a = asistenteRepository.saveAndFlush(a);
        return new AsistenteFilaDto(a.getId(), a.getCodigo(), a.getTipo(), a.getEstadoInscripcion(),
                contarAsistencias(a), a.getUuid() != null);
    }

    /** Quien ya fue marcado en algún evento no se elimina: se perdería evidencia de terreno (caso §4.1). */
    @Transactional
    public void eliminar(Long id) {
        Asistente a = buscar(id);
        if (asistenciaRepository.existsByAsistenteId(id)) {
            throw new ConflictoException("El asistente ya tiene asistencias registradas y no se puede eliminar");
        }
        asistenteRepository.delete(a);
    }

    private Asistente buscar(Long id) {
        return asistenteRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("El asistente no existe"));
    }

    private long contarAsistencias(Asistente a) {
        return asistenciaRepository.countByAsistenteId(a.getId());
    }
}
