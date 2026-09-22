import { Card, Tag, Typography } from 'antd';
const { Title, Paragraph, Text } = Typography;
// Shared stub for pages whose owner has not implemented them yet.
// Renders the ownership card: who builds this page, which api/* functions to
// call, the wireframe reference, and the concrete TODO list. `children` render
// below the card content (the Login stub uses this slot for its temporary dev
// sign-in button). Plain <ul> instead of antd List (List is deprecated in v6).
export default function PagePlaceholder({ owner, page, wireframe, apis = [], todos = [], children }) {
    return (<Card style={{ maxWidth: 720, margin: '48px auto' }}>
      <Tag color="orange">PLACEHOLDER — waiting for owner implementation</Tag>
      <Title level={3} style={{ marginTop: 12 }}>{page}</Title>
      <Paragraph>
        Owner: <Text strong>{owner}</Text>. This page is intentionally a stub.
        The foundation around it is ready — routing, auth guard, state stores,
        API functions, shared components and mock mode. Build the page UI on
        top of those pieces; do not rebuild them.
      </Paragraph>
      {wireframe && (<Paragraph>
          Wireframe: <Text code>{wireframe}</Text>
        </Paragraph>)}
      <Title level={5}>API functions to call (already implemented in src/api/)</Title>
      <ul style={{ paddingLeft: 20, marginBottom: 16 }}>
        {apis.map((a) => <li key={a} style={{ marginBottom: 4 }}><Text code>{a}</Text></li>)}
      </ul>
      <Title level={5}>TODO</Title>
      <ul style={{ paddingLeft: 20 }}>
        {todos.map((t) => <li key={t} style={{ marginBottom: 4 }}>{t}</li>)}
      </ul>
      {children}
    </Card>);
}
