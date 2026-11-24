package ar.turnos.odontologia.usuarios.domain;

import java.util.Random;

/**
 * Contraseña temporal para una cuenta del personal (C-41 design D2): 14 caracteres de un alfabeto sin los que se
 * confunden al dictarlos (0, O, 1, l, I), con al menos una mayúscula, una minúscula y un dígito.
 * Recibe el {@link Random} para poder usar {@code SecureRandom} en producción.
 */
public final class GeneradorContrasenaTemporal {

    public static final int LARGO = 14;

    private static final String MAYUSCULAS = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String MINUSCULAS = "abcdefghijkmnopqrstuvwxyz";
    private static final String DIGITOS = "23456789";
    private static final String TODOS = MAYUSCULAS + MINUSCULAS + DIGITOS;

    private final Random azar;

    public GeneradorContrasenaTemporal(Random azar) {
        this.azar = azar;
    }

    public String generar() {
        char[] contrasena = new char[LARGO];
        contrasena[0] = elegir(MAYUSCULAS);
        contrasena[1] = elegir(MINUSCULAS);
        contrasena[2] = elegir(DIGITOS);
        for (int i = 3; i < LARGO; i++) {
            contrasena[i] = elegir(TODOS);
        }
        // mezcla (Fisher-Yates) para que las tres clases obligatorias no queden siempre al principio
        for (int i = LARGO - 1; i > 0; i--) {
            int j = azar.nextInt(i + 1);
            char aux = contrasena[i];
            contrasena[i] = contrasena[j];
            contrasena[j] = aux;
        }
        return new String(contrasena);
    }

    private char elegir(String alfabeto) {
        return alfabeto.charAt(azar.nextInt(alfabeto.length()));
    }
}
