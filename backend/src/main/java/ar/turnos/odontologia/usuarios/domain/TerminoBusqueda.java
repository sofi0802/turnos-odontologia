package ar.turnos.odontologia.usuarios.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Término con el que el personal busca un paciente (design D2 a D4). Solo dígitos: DNI por prefijo. Con letras:
 * hasta 5 palabras, normalizadas sin tildes y en minúsculas, que tienen que aparecer en el nombre o el apellido.
 * El término es un dato personal: el {@code toString()} no lo incluye y los mensajes de error no lo repiten.
 */
public final class TerminoBusqueda {

    public static final int MAXIMO_CARACTERES = 50;
    public static final int MAXIMO_PALABRAS = 5;
    private static final int MINIMO_LETRAS = 2;
    private static final int MINIMO_DIGITOS = 3;

    // Las mismas letras que traduce el adaptador SQL: translate(lower(x), 'áéíóúüñ', 'aeiouun').
    private static final String CON_TILDE = "áéíóúüñ";
    private static final String SIN_TILDE = "aeiouun";

    private final boolean porDni;
    private final String dni;
    private final List<String> palabras;

    private TerminoBusqueda(boolean porDni, String dni, List<String> palabras) {
        this.porDni = porDni;
        this.dni = dni;
        this.palabras = List.copyOf(palabras);
    }

    public static TerminoBusqueda de(String crudo) {
        String texto = crudo == null ? "" : crudo.strip();
        if (texto.length() > MAXIMO_CARACTERES) {
            throw new BusquedaInvalidaException(BusquedaInvalidaException.MENSAJE_LARGO);
        }
        if (soloDigitos(texto)) {
            if (texto.length() < MINIMO_DIGITOS) {
                throw new BusquedaInvalidaException(BusquedaInvalidaException.MENSAJE_CORTO);
            }
            return new TerminoBusqueda(true, texto, List.of());
        }
        if (texto.length() < MINIMO_LETRAS) {
            throw new BusquedaInvalidaException(BusquedaInvalidaException.MENSAJE_CORTO);
        }
        List<String> palabras = new ArrayList<>();
        for (String palabra : texto.split("\\s+")) {
            if (palabras.size() < MAXIMO_PALABRAS) {
                palabras.add(normalizar(palabra));
            }
        }
        return new TerminoBusqueda(false, null, palabras);
    }

    public boolean porDni() {
        return porDni;
    }

    /** Dígitos del DNI buscado; solo tiene sentido si {@link #porDni()}. */
    public String dni() {
        return dni;
    }

    /** Palabras normalizadas, sin escapar. Vacía si se busca por DNI. */
    public List<String> palabras() {
        return palabras;
    }

    /** Palabras con {@code \}, {@code %} y {@code _} escapados con {@code \} para un {@code LIKE ... ESCAPE '\'}. */
    public List<String> palabrasParaLike() {
        return palabras.stream()
                .map(p -> p.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_"))
                .toList();
    }

    private static String normalizar(String palabra) {
        StringBuilder normalizada = new StringBuilder();
        for (char c : palabra.toLowerCase(Locale.ROOT).toCharArray()) {
            int i = CON_TILDE.indexOf(c);
            normalizada.append(i >= 0 ? SIN_TILDE.charAt(i) : c);
        }
        return normalizada.toString();
    }

    private static boolean soloDigitos(String texto) {
        if (texto.isEmpty()) {
            return false;
        }
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    /** Sin el término: un objeto que termine en un log por accidente no filtra lo buscado. */
    @Override
    public String toString() {
        return "TerminoBusqueda[" + (porDni ? "dni" : "texto") + "]";
    }
}
