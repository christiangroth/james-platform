export class ApiClient {
  constructor(baseUrl) {
    this.baseUrl = baseUrl;
  }

  async getRequest(path = '') {
    return this._apiRequest(`${this.baseUrl}${path}`, {
      method: 'GET'
    });
  }

  async postRequest(path = '', body = null) {
    return this._apiRequest(`${this.baseUrl}${path}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: body ? JSON.stringify(body) : '{}'
    });
  }

  async putRequest(path = '', body = null) {
    return this._apiRequest(`${this.baseUrl}${path}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: body ? JSON.stringify(body) : null
    });
  }

  async deleteRequest(path = '', body = null) {
    return this._apiRequest(`${this.baseUrl}${path}`, {
      method: 'DELETE',
      headers: body ? { 'Content-Type': 'application/json' } : {},
      body: body ? JSON.stringify(body) : null
    });
  }

  // "Privat" durch Konvention (Underscore), nicht durch #
  async _apiRequest(url, options = {}) {
    try {
      const response = await fetch(url, options);

      if (!response.ok) {
        const error = await response.json().catch(() => ({}));
        throw new Error(error.message || `Request failed: ${response.status} ${response.statusText}`);
      }

      return await response.json().catch(() => null);
    } catch (err) {
      console.error(`API Error [${options.method || 'GET'}] ${url}:`, err);
      throw err;
    }
  }

  async executeAction(callFn, action) {
    const {
      successMessage,
      errorMessage,
      alertHandler,
      onSuccess,
      onError,
      onFinally
    } = action;

    try {
      const result = await callFn();

      if (onSuccess) {
        onSuccess(result);
      }

      if (successMessage && alertHandler) {
        alertHandler.showAlert(successMessage, 'success');
      }

      return result;
    } catch (err) {
      const finalErrorMessage = errorMessage
        ? `${errorMessage}: ${err.message}`
        : err.message;

      if (alertHandler) {
        alertHandler.showAlert(finalErrorMessage, 'danger');
      }

      if (onError) {
        onError(err);
      }

      throw err;
    } finally {
      if (onFinally) {
        onFinally();
      }
    }
  }
}
