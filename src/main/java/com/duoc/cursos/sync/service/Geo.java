package com.duoc.cursos.sync.service;

import com.duoc.cursos.model.Evento;
import com.duoc.cursos.model.enums.EstadoValidacion;

/**
 * Cálculo de distancia y clasificación de una marcación respecto al recinto del evento.
 *
 * El GPS es referencial (caso §3.3): este código CLASIFICA, nunca decide rechazar.
 */
public final class Geo {

    /** Radio medio de la Tierra (IUGG), en metros. */
    private static final double RADIO_TIERRA_METROS = 6_371_008.8;

    private Geo() {
    }

    /** Resultado de clasificar una marcación. distanciaMetros es null cuando no hay ubicación utilizable. */
    public record Clasificacion(EstadoValidacion estado, Integer distanciaMetros) {
    }

    /** Distancia entre dos puntos sobre la esfera (fórmula de haversine), en metros. */
    public static double distanciaMetros(double lat1, double lon1, double lat2, double lon2) {
        double fi1 = Math.toRadians(lat1);
        double fi2 = Math.toRadians(lat2);
        double dFi = Math.toRadians(lat2 - lat1);
        double dLambda = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dFi / 2) * Math.sin(dFi / 2)
                + Math.cos(fi1) * Math.cos(fi2) * Math.sin(dLambda / 2) * Math.sin(dLambda / 2);
        return 2 * RADIO_TIERRA_METROS * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /**
     * SIN_UBICACION si falta cualquiera de las coordenadas de la marcación o del evento (una latitud
     * sin longitud no es una posición). Si no, VALIDA cuando la distancia no supera el radio del evento
     * y FUERA_DE_RADIO en caso contrario.
     */
    public static Clasificacion clasificar(Double latitud, Double longitud, Evento evento) {
        if (latitud == null || longitud == null
                || evento.getLatitud() == null || evento.getLongitud() == null) {
            return new Clasificacion(EstadoValidacion.SIN_UBICACION, null);
        }

        long distancia = Math.round(distanciaMetros(latitud, longitud, evento.getLatitud(), evento.getLongitud()));
        int radio = evento.getRadioMetros() != null ? evento.getRadioMetros() : Evento.RADIO_POR_DEFECTO_METROS;
        EstadoValidacion estado = distancia <= radio ? EstadoValidacion.VALIDA : EstadoValidacion.FUERA_DE_RADIO;
        return new Clasificacion(estado, (int) distancia);
    }
}
