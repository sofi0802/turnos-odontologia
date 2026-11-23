import './styles.css';
import { iniciarApp } from './app.ts';
import { traducirEntrada } from './shared/ui/entrada.ts';

const raiz = document.querySelector<HTMLElement>('#app');
if (raiz) {
  // Los links de los avisos (C-11) usan rutas de camino: se traducen antes del router.
  traducirEntrada();
  iniciarApp(raiz);
}
