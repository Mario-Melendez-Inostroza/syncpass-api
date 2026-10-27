package com.duoc.cursos.informe.dto;

import com.duoc.cursos.sync.dto.ComentarioDto;
import com.duoc.cursos.sync.dto.ProyectoDto;

import java.time.Instant;
import java.util.List;

/** Informe de impacto consolidado (caso §2.2 punto 4 y §2.4: informes automáticos de cierre). */
public final class InformeDtos {

    private InformeDtos() {
    }

    /**
     * Un evento del informe.
     *
     * @param tasaRespuesta encuestas recibidas sobre asistentes marcados (0 a 1); null si nadie marcó.
     * @param quorumCumplido null si el evento no define un quórum mínimo.
     */
    public record EventoInforme(
            Long eventoId,
            String nombre,
            Instant fechaHoraProgramada,
            Integer cuposMaximos,
            Integer quorumMinimo,
            Boolean quorumCumplido,
            long totalMarcados,
            long validas,
            long fueraDeRadio,
            long sinUbicacion,
            long encuestasRecibidas,
            Double tasaRespuesta,
            Double satisfaccionPromedio,
            Double utilidadPromedio,
            List<ComentarioDto> comentarios) {
    }

    /**
     * Totales de un proyecto o de todo el informe.
     *
     * @param asistentesUnicos personas distintas que asistieron a al menos un evento.
     * @param eventosConQuorumMinimo cuántos eventos definen un mínimo; solo esos cuentan para "cumplidos".
     */
    public record Totales(
            int eventos,
            int eventosConQuorumMinimo,
            int eventosQuorumCumplido,
            long totalMarcados,
            long asistentesUnicos,
            long encuestasRecibidas,
            Double tasaRespuesta,
            Double satisfaccionPromedio,
            Double utilidadPromedio) {
    }

    public record ProyectoInforme(ProyectoDto proyecto, List<EventoInforme> eventos, Totales totales) {
    }

    public record InformeResponse(Instant generadoEn, List<ProyectoInforme> proyectos, Totales totales) {
    }
}
