package com.duoc.cursos.sync;

import com.duoc.cursos.model.enums.EstadoInscripcion;
import com.duoc.cursos.model.enums.TipoParticipante;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Los valores de estos enums son parte del contrato con la web y la app: si cambian, hay que cambiar los tres. */
class TipoParticipanteTest {

    @Test
    @DisplayName("los tipos de participante son los del caso (§3.2): Estudiante, Vecino y Docente")
    void tipos() {
        assertEquals(List.of("ESTUDIANTE", "VECINO", "DOCENTE"),
                java.util.Arrays.stream(TipoParticipante.values()).map(Enum::name).toList());
    }

    @Test
    @DisplayName("el estado de inscripción distingue a los inscritos de quienes llegan sin inscripción")
    void inscripcion() {
        assertEquals(List.of("INSCRITO", "SIN_INSCRIPCION"),
                java.util.Arrays.stream(EstadoInscripcion.values()).map(Enum::name).toList());
    }
}
