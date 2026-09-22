// Owner: Ziyuan Xu (auth). Wireframe: wireframes/02_Register.svg
import PagePlaceholder from '../components/PagePlaceholder';
export default function Register() {
    return (<PagePlaceholder owner="Ziyuan Xu" page="Register" wireframe="wireframes/02_Register.svg" apis={['useAuth().signup(username, password, email)  ->  POST /api/auth/register  (body is contract-TBD — confirm with the backend first)']} todos={[
            'Build the form with antd <Form>: username / password / confirm password / email',
            'Client-side validation: email format, password confirmation match',
            "On success the store hydrates the session; navigate('/dashboard')",
            'On failure: surface the server error with antd message',
            'Link back to /login',
        ]}/>);
}
