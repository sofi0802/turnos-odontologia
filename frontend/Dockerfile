# Frontend de turnos-odontologia en modo desarrollo (C-52, design D2).
# El contexto de build es la raíz del repo: los estilos importan ../design-system.
# El código de frontend/ y design-system/ se monta como volumen desde el compose para que Vite
# recargue al editar; acá solo se instalan las dependencias de Linux.

FROM node:22-bookworm-slim
WORKDIR /app/frontend

COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

# Copia de respaldo para usar la imagen sin volúmenes (docker run); el compose la tapa con el código del host.
COPY design-system /app/design-system
COPY frontend /app/frontend

EXPOSE 5173
# --host 0.0.0.0 por línea de comando para no tocar vite.config.ts (dueño: Enzo).
CMD ["npm", "run", "dev", "--", "--host", "0.0.0.0", "--port", "5173", "--strictPort"]
