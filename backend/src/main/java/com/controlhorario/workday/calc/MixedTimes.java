package com.controlhorario.workday.calc;

import java.time.LocalTime;

/**
 * Tramos de un día MIXTO: entrada y salida en la oficina y en casa, en cualquier orden. El hueco entre
 * los dos tramos (desplazamiento, comida...) no es tiempo trabajado; las pausas van dentro de un tramo.
 */
public record MixedTimes(LocalTime officeStart, LocalTime officeEnd, LocalTime homeStart, LocalTime homeEnd) {

    public boolean complete() {
        return officeStart != null && officeEnd != null && homeStart != null && homeEnd != null;
    }

    /** Primera entrada del día; null si falta alguna hora. */
    public LocalTime start() {
        if (!complete()) {
            return null;
        }
        return officeStart.isBefore(homeStart) ? officeStart : homeStart;
    }

    /** Última salida del día; null si falta alguna hora. */
    public LocalTime end() {
        if (!complete()) {
            return null;
        }
        return officeEnd.isAfter(homeEnd) ? officeEnd : homeEnd;
    }
}
