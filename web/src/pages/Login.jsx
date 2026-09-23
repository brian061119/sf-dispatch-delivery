// Owner: Ziyuan Xu (auth). Wireframe: wireframes/01_Login.svg
// Placeholder: route works, implementation pending. Use store/auth.js (useAuth().login).
export default function Login() {
    return (<div style={{ padding: 48 }}>
      <h2>Login</h2>
      <p>Placeholder — owner: Ziyuan Xu. Implementation pending.</p>
      {/* DEMO-PHASE ONLY — this hint disappears together with this stub when the
          real Login lands. Until then the auth guard accepts ANY token string. */}
      <p style={{ color: '#888', maxWidth: 520 }}>
        Demo shortcut (no backend needed): open DevTools → Application → Local Storage
        → add key <code>token</code> with any value → refresh. You are now "logged
        in" and every protected page opens. Delete the key (or click Log out) to
        become a guest again.
      </p>
    </div>);
}
