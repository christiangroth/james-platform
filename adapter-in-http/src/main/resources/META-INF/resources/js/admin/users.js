import { ApiClient } from '../api-client.js';
import { AlertHandler } from '../alert-handler.js';
import { ModalHandler } from '../modal-handler.js';

export function userTable() {
  return {
    BASE_URL: '/api/users',

    loading: true,
    users: [],

    api: new ApiClient('/api/users'),
    alert: new AlertHandler(),
    modal: new ModalHandler(),

    async fetchUsers() {
      this.loading = true;

      await this.api.executeAction(() => this.api.getRequest(), {
        errorMessage: 'Failed to load users. Please try again later',
        alertHandler: this.alert,
        onSuccess: (users) => this.users = users,
        onFinally: () => this.loading = false
      });
    },

    async toggleUserStatus(user) {
      const isActive = user.status === 'ACTIVE';
      const action = isActive ? 'deactivate' : 'activate';
      const body = isActive ? { reason: this.modal.input.reason || '' } : null;

      await this.api.executeAction(() => this.api.postRequest(`/${user.id}/${action}`, body), {
        successMessage: `User ${action}d successfully!`,
        errorMessage: `Failed to ${action} user`,
        alertHandler: this.alert,
        onSuccess: () => this.fetchUsers(),
        onFinally: () => this.modal.close()
      });
    },

    async deleteUser(user) {
      await this.api.executeAction(this.api.deleteRequest(`/${user.id}`), {
        successMessage: 'User deleted successfully!',
        errorMessage: 'Failed to delete user',
        alertHandler: this.alert,
        onSuccess: () => this.fetchUsers(),
        onFinally: () => this.modal.close()
      });
    },

    async createUser() {
      const { username, password, confirmPassword, roles } = this.modal.input;

      if (password !== confirmPassword) {
        this.alert.showAlert('Passwords do not match', 'danger');
        return;
      }

      const body = {
        username,
        password,
        roles: roles ? roles.split(',').map(r => r.trim()) : []
      };

      await this.api.executeAction(() => this.api.putRequest('', body), {
        successMessage: 'User created successfully!',
        errorMessage: 'Failed to create user',
        alertHandler: this.alert,
        onSuccess: () => this.fetchUsers(),
        onFinally: () => this.modal.close()
      });
    },

    async resetPassword(user) {
      const { newPassword, confirmPassword } = this.modal.input;

      if (newPassword !== confirmPassword) {
        this.alert.showAlert('Passwords do not match', 'danger');
        return;
      }

      await this.api.executeAction(() => this.api.deleteRequest(`/${user.id}/password`, { password: newPassword }), {
        successMessage: 'Password reset successfully!',
        errorMessage: 'Failed to reset password',
        alertHandler: this.alert,
        onFinally: () => this.modal.close()
      });
    },

    confirmToggleStatus(user) {
      const isActive = user.status === 'ACTIVE';
      this.modal.open(
        isActive ? 'Deactivate User' : 'Activate User',
        isActive ? 'Please provide a reason for deactivation (optional):' : 'Are you sure you want to activate this user?',
        () => this.toggleUserStatus(user),
        { reason: '' }
      );
    },

    confirmDeleteUser(user) {
      this.modal.confirm(
        'Delete User',
        `Are you sure you want to delete user ${user.username}? This action cannot be undone.`,
        () => this.deleteUser(user)
      );
    },

    showCreateUserForm() {
      this.modal.form(
        'Create New User',
        () => this.createUser(),
        {
          username: '',
          password: '',
          confirmPassword: '',
          roles: 'USER'
        }
      );
    },

    showResetPasswordForm(user) {
      this.modal.form(
        'Reset Password',
        () => this.resetPassword(user),
        {
          newPassword: '',
          confirmPassword: ''
        }
      );
    },

    getStatusBadgeClass(status) {
      return {
        'bg-success': status === 'ACTIVE',
        'bg-danger': status === 'INACTIVE'
      };
    },

    getPasswordStatusBadgeClass(status) {
      return {
        'bg-success': status === 'PERMANENT',
        'bg-warning': status === 'ONE_TIME'
      };
    },

    formatStatus(status) {
      return { 'ACTIVE': 'Active', 'INACTIVE': 'Inactive' }[status] || status;
    },

    formatPasswordStatus(status) {
      return { 'PERMANENT': 'Permanent', 'ONE_TIME': 'One-Time' }[status] || status;
    }
  };
}

// Global verfügbar machen für AlpineJS
window.userTable = userTable;
