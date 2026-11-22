// Nombre visible del producto. Cambiarlo acá actualiza todas las vistas previas HTML
// que marcan el texto con [data-brand-name]. Nombre definitivo: (TBD), ver brand.md.
// Script clásico (no módulo) para que funcione también abriendo el HTML con doble clic (file://).
(function () {
  var BRAND_NAME = 'Turnos odontología';
  window.BRAND_NAME = BRAND_NAME;

  function aplicarMarca() {
    var nodos = document.querySelectorAll('[data-brand-name]');
    for (var i = 0; i < nodos.length; i++) nodos[i].textContent = BRAND_NAME;
    if (document.title.indexOf('{marca}') !== -1) {
      document.title = document.title.replace('{marca}', BRAND_NAME);
    }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', aplicarMarca);
  } else {
    aplicarMarca();
  }
})();
