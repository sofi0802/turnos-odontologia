-- C-41 cuentas-del-personal (design D5): marca de contraseña temporal.
-- Las cuentas que ya existen, incluido el admin inicial, quedan en FALSE.
ALTER TABLE usuario ADD COLUMN debe_cambiar_contrasena BOOLEAN NOT NULL DEFAULT FALSE;
