package com.duoc.cursos.inscripcion;

import com.duoc.cursos.inscripcion.dto.MiPerfilDto;
import com.duoc.cursos.model.Asistente;
import com.duoc.cursos.model.enums.TipoParticipante;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MiPerfilDtoTest {

    @Test
    @DisplayName("el QR del asistente es ASISTENTE_{id}, el formato que lee la app del monitor")
    void qr() {
        Asistente a = new Asistente();
        a.setId(42L);
        a.setCodigo("REG-AB12CD34");
        a.setTipo(TipoParticipante.VECINO);

        MiPerfilDto dto = MiPerfilDto.de(a);

        assertEquals(42L, dto.id());
        assertEquals("REG-AB12CD34", dto.codigo());
        assertEquals(TipoParticipante.VECINO, dto.tipo());
        assertEquals("ASISTENTE_42", dto.qr());
    }
}
