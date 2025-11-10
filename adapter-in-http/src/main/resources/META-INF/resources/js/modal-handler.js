export class ModalHandler {
  constructor() {
    this.show = false;
    this.title = '';
    this.message = '';
    this.action = null;
    this.input = {};
  }

  open(title, message, action, input = {}) {
    this.show = true;
    this.title = title;
    this.message = message;
    this.action = action;
    this.input = input;
  }

  close() {
    this.show = false;
    this.title = '';
    this.message = '';
    this.action = null;
    this.input = {};
  }

  confirm(title, message, action) {
    this.open(title, message, action);
  }

  form(title, action, fields = {}) {
    this.open(title, '', action, fields);
  }
}
