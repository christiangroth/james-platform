// In your users.js
import { UserApi, Configuration } from '../open-api/index.js';

const config = new Configuration({
    basePath: '/api' // Your API base path
});

const userApi = new UserApi(config);

// Now you can use it in your Alpine component
function userTable() {
    return {
        users: [],
        loading: false,
        error: null,
        
        async fetchUsers() {
            this.loading = true;
            this.error = null;
            try {
                // The generated client will handle the fetch calls
                this.users = await userApi.getAllUsers();
            } catch (e) {
                console.error('API Error:', e);
                this.error = e.message;
            } finally {
                this.loading = false;
            }
        }
    };
}
