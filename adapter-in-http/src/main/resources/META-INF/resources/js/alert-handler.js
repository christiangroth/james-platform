export class AlertHandler {
  constructor() {
    this.show = false;
    this.message = '';
    this.type = 'success';
  }

  showAlert(message, type = 'success') {
    this.show = true;
    this.message = message;
    this.type = type;

    setTimeout(() => {
      this.show = false;
    }, 10000);
  }

  hide() {
    this.show = false;
  }
}
