package com.duoc.cursos.sync;

import com.duoc.cursos.model.Evento;
import com.duoc.cursos.model.enums.EstadoValidacion;
import com.duoc.cursos.sync.service.Geo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Pruebas puras de la clasificación geográfica: sin Spring, sin base de datos. */
class GeoTest {

    private static final double LAT = -33.5121;
    private static final double LON = -70.6003;

    private static Evento evento(Double lat, Double lon, Integer radio) {
        Evento e = new Evento();
        e.setLatitud(lat);
        e.setLongitud(lon);
        e.setRadioMetros(radio);
        return e;
    }

    @Test
    @DisplayName("Distancia: un grado de latitud mide ~111,19 km (margen de 1 m)")
    void distancia_ungradoDeLatitud() {
        assertEquals(111_194.9, Geo.distanciaMetros(0, 0, 1, 0), 1.0);
    }

    @Test
    @DisplayName("Distancia: el mismo punto está a 0 m")
    void distancia_mismoPunto() {
        assertEquals(0.0, Geo.distanciaMetros(LAT, LON, LAT, LON), 0.001);
    }

    @Test
    @DisplayName("Distancia: es simétrica")
    void distancia_simetrica() {
        assertEquals(Geo.distanciaMetros(LAT, LON, -33.6, -70.5), Geo.distanciaMetros(-33.6, -70.5, LAT, LON), 0.001);
    }

    @Test
    @DisplayName("Dentro del radio -> VALIDA y devuelve la distancia")
    void dentroDelRadio_valida() {
        // ~0,0003 grados de latitud son ~33 m.
        Geo.Clasificacion c = Geo.clasificar(LAT + 0.0003, LON, evento(LAT, LON, 200));

        assertEquals(EstadoValidacion.VALIDA, c.estado());
        assertEquals(33, c.distanciaMetros(), 1);
    }

    @Test
    @DisplayName("Fuera del radio -> FUERA_DE_RADIO (se clasifica, no se rechaza)")
    void fueraDelRadio() {
        // ~0,01 grados de latitud son ~1,1 km.
        Geo.Clasificacion c = Geo.clasificar(LAT + 0.01, LON, evento(LAT, LON, 200));

        assertEquals(EstadoValidacion.FUERA_DE_RADIO, c.estado());
        assertNotNull(c.distanciaMetros());
        assertTrue(c.distanciaMetros() > 200);
    }

    @Test
    @DisplayName("Exactamente en el borde del radio -> VALIDA")
    void enElBordeDelRadio() {
        Evento e = evento(LAT, LON, 100);
        int distancia = Geo.clasificar(LAT + 0.0009, LON, e).distanciaMetros();

        Geo.Clasificacion borde = Geo.clasificar(LAT + 0.0009, LON, evento(LAT, LON, distancia));
        assertEquals(EstadoValidacion.VALIDA, borde.estado());

        Geo.Clasificacion uno_menos = Geo.clasificar(LAT + 0.0009, LON, evento(LAT, LON, distancia - 1));
        assertEquals(EstadoValidacion.FUERA_DE_RADIO, uno_menos.estado());
    }

    @Test
    @DisplayName("Sin coordenadas de la marcación -> SIN_UBICACION, sin distancia")
    void sinCoordenadas() {
        Geo.Clasificacion c = Geo.clasificar(null, null, evento(LAT, LON, 200));

        assertEquals(EstadoValidacion.SIN_UBICACION, c.estado());
        assertNull(c.distanciaMetros());
    }

    @Test
    @DisplayName("Latitud sin longitud (o al revés) no es una posición -> SIN_UBICACION")
    void coordenadasIncompletas() {
        assertEquals(EstadoValidacion.SIN_UBICACION, Geo.clasificar(LAT, null, evento(LAT, LON, 200)).estado());
        assertEquals(EstadoValidacion.SIN_UBICACION, Geo.clasificar(null, LON, evento(LAT, LON, 200)).estado());
    }

    @Test
    @DisplayName("Evento sin coordenadas -> SIN_UBICACION aunque la marcación las traiga")
    void eventoSinCoordenadas() {
        Geo.Clasificacion c = Geo.clasificar(LAT, LON, evento(null, null, 200));

        assertEquals(EstadoValidacion.SIN_UBICACION, c.estado());
        assertNull(c.distanciaMetros());
    }

    @Test
    @DisplayName("Evento sin radio usa el radio por defecto")
    void radioPorDefecto() {
        // ~0,0009 grados son ~100 m: dentro de los 200 m por defecto.
        assertEquals(EstadoValidacion.VALIDA, Geo.clasificar(LAT + 0.0009, LON, evento(LAT, LON, null)).estado());
        // ~0,005 grados son ~556 m: fuera.
        assertEquals(EstadoValidacion.FUERA_DE_RADIO, Geo.clasificar(LAT + 0.005, LON, evento(LAT, LON, null)).estado());
    }
}
