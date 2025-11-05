function userTable() {
  return {
    loading: true,
    error: null,
    users: [],
    showModal: false,
    modalTitle: '',
    modalMessage: '',
    modalAction: null,
    modalInput: {},
    alert: {
      show: false,
      message: '',
      type: 'success'
    },

    async fetchUsers() {
      this.loading = true;
      this.error = null;

      try {
        const response = await fetch('/api/users');
        if (!response.ok) {
          throw new Error(`Failed to fetch users: ${response.status} ${response.statusText}`);
        }
        this.users = await response.json();
      } catch (err) {
        console.error('Error fetching users:', err);
        this.showAlert('Failed to load users. Please try again later.', 'danger');
      } finally {
        this.loading = false;
      }
    },

    async toggleUserStatus(user) {
      const isActive = user.status === 'ACTIVE';
      const action = isActive ? 'deactivate' : 'activate';
      const reason = this.modalInput.reason || '';
      
      try {
        const url = `/api/users/${user.id}/${action}`;
        const response = await fetch(url, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          },
          body: isActive ? JSON.stringify({ reason }) : '{}'
        });

        if (!response.ok) {
          const error = await response.json();
          throw new Error(error.message || `Failed to ${action} user`);
        }

        this.showAlert(`User ${action}d successfully!`, 'success');
        this.fetchUsers();
      } catch (err) {
        console.error(`Error ${action}ing user:`, err);
        this.showAlert(`Failed to ${action} user: ${err.message}`, 'danger');
      } finally {
        this.closeModal();
      }
    },

    confirmToggleStatus(user) {
      const isActive = user.status === 'ACTIVE';
      this.modalTitle = isActive ? 'Deactivate User' : 'Activate User';
      this.modalMessage = isActive 
        ? 'Please provide a reason for deactivation (optional):' 
        : 'Are you sure you want to activate this user?';
      
      this.modalAction = () => this.toggleUserStatus(user);
      this.modalInput = { reason: '' };
      this.showModal = true;
    },

    showAlert(message, type = 'success') {
      this.alert = { show: true, message, type };
      setTimeout(() => {
        this.alert.show = false;
      }, 10000);
    },

    closeModal() {
      this.showModal = false;
      this.modalTitle = '';
      this.modalMessage = '';
      this.modalAction = null;
      this.modalInput = {};
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
      const statusMap = {
        'ACTIVE': 'Active',
        'INACTIVE': 'Inactive',
      };
      return statusMap[status] || status;
    },

    formatPasswordStatus(status) {
      const statusMap = {
        'PERMANENT': 'Permanent',
        'ONE_TIME': 'One-Time',
      };
      return statusMap[status] || status;
    }
  };
}
