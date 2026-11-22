// jsdom no implementa showModal ni close de <dialog> (design D5): este polyfill
// mínimo alcanza para los tests; el navegador real usa los nativos.
if (typeof HTMLDialogElement !== 'undefined' && typeof HTMLDialogElement.prototype.showModal !== 'function') {
  HTMLDialogElement.prototype.showModal = function showModal(this: HTMLDialogElement): void {
    this.open = true;
  };
  HTMLDialogElement.prototype.close = function close(this: HTMLDialogElement, valor?: string): void {
    if (!this.open) {
      return;
    }
    this.open = false;
    if (valor !== undefined) {
      this.returnValue = valor;
    }
    this.dispatchEvent(new Event('close'));
  };
}
