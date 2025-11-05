// user-table.js
function userTable() {
  return {
    loading: true,
    error: null,
    users: [],

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
        this.error = 'Failed to load users. Please try again later.';
      } finally {
        this.loading = false;
      }
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
