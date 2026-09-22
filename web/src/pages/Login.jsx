// Owner: Ziyuan Xu (auth). Wireframe: wireframes/01_Login.svg
import { App, Button } from 'antd';
import { useNavigate } from 'react-router-dom';
import PagePlaceholder from '../components/PagePlaceholder';
import { useAuth } from '../store/auth';
export default function Login() {
    return (<PagePlaceholder owner="Ziyuan Xu" page="Login" wireframe="wireframes/01_Login.svg" apis={['useAuth().login(username, password)  ->  POST /api/auth/login  (store/auth.js wraps api/auth.js)']} todos={[
            'Build the form with antd <Form>: username + password, both required',
            "On submit: await useAuth().login(...) then navigate('/dashboard')",
            'On failure: surface the error with antd message (App.useApp())',
            'Link to /register for new users',
            'Remove the temporary dev sign-in below once the real form works',
        ]}>
      <DevSignIn />
    </PagePlaceholder>);
}
// TEMPORARY SCAFFOLD — one-click mock sign-in so teammates can reach the
// protected pages while this page is still a stub. REMOVE when the real form
// lands. (Any credentials work in mock mode.)
function DevSignIn() {
    const login = useAuth((s) => s.login);
    const nav = useNavigate();
    const { message } = App.useApp();
    async function signIn() {
        try {
            await login('demo', 'demo');
            nav('/dashboard');
        }
        catch {
            message.error('Dev sign-in failed — is the backend or VITE_MOCK=1 running?');
        }
    }
    return (<Button block onClick={signIn} style={{ marginTop: 16 }}>
      [TEMP] Dev sign-in — scaffolding only, delete with the real form
    </Button>);
}
