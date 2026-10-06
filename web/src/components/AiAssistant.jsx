import { useEffect, useRef } from 'react';
import { Button, Card, Drawer, Input, Space, Tag, Typography } from 'antd';
import { CommentOutlined, RobotOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { useChat } from '../store/chat';
import { useWizard } from '../store/wizard';

const { Text } = Typography;

// Floating AI assistant (bottom-right on every page).
// Cards: order → jump to live tracking; quote → option prices; prefill → push
// the AI draft into the order wizard. The assistant itself never writes: every
// action button hands control back to the user.
export function AiAssistant() {
    const { open, busy, messages, toggle, send } = useChat();
    const navigate = useNavigate();
    const wizard = useWizard();
    const inputRef = useRef(null);
    const scrollRef = useRef(null);

    useEffect(() => {
        scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight });
    }, [messages, busy]);

    function submit() {
        const el = inputRef.current;
        const value = el?.input?.value ?? '';
        if (el?.input) el.input.value = '';
        send(value);
    }

    function usePrefill(prefill) {
        if (prefill) wizard.prefill(prefill);
        toggle();
        navigate('/order/new');
    }

    return (
        <>
            <Button
                type="primary"
                shape="circle"
                size="large"
                icon={<CommentOutlined />}
                onClick={toggle}
                style={{ position: 'fixed', right: 28, bottom: 28, width: 56, height: 56, zIndex: 1000, boxShadow: '0 4px 16px rgba(22,119,255,.45)', background: '#722ed1', borderColor: '#722ed1' }}
            />
            <Drawer
                title={<Space><RobotOutlined style={{ color: '#722ed1' }} />Delivery assistant</Space>}
                placement="right"
                width={380}
                open={open}
                onClose={toggle}
                styles={{ body: { display: 'flex', flexDirection: 'column', padding: 16 } }}
            >
                <div ref={scrollRef} style={{ flex: 1, overflowY: 'auto', paddingRight: 4 }}>
                    {!messages.length && (
                        <Card size="small" style={{ background: '#f9f0ff', border: '1px solid #d3adf7' }}>
                            <Text>Hi! Ask me things like:</Text>
                            <div style={{ marginTop: 8, display: 'grid', gap: 6 }}>
                                <Button size="small" onClick={() => send('Where is my delivery TC-8ZK2QA?')}>“Where is my delivery TC-8ZK2QA?”</Button>
                                <Button size="small" onClick={() => send('How much for a 2kg package?')}>“How much for a 2kg package?”</Button>
                                <Button size="small" onClick={() => send('Send a 3kg fragile cake to Mission')}>“Send a 3kg fragile cake to Mission”</Button>
                            </div>
                        </Card>
                    )}
                    {messages.map((m, i) => (
                        <div key={i} style={{ display: 'flex', justifyContent: m.role === 'user' ? 'flex-end' : 'flex-start', margin: '10px 0' }}>
                            <div style={{ maxWidth: '85%', padding: '10px 12px', borderRadius: 12, background: m.role === 'user' ? '#1677ff' : '#f5f5f5', color: m.role === 'user' ? '#fff' : 'inherit' }}>
                                <div style={{ whiteSpace: 'pre-wrap' }}>{m.content}</div>
                                {(m.cards ?? []).map((card, j) => (
                                    <Card key={j} size="small" style={{ marginTop: 8 }}>
                                        {card.type === 'order' && (
                                            <Space direction="vertical" size={4}>
                                                <Text strong>{card.orderId} <Tag color={card.status === 'DELIVERED' ? 'green' : card.status === 'CANCELLED' ? 'red' : 'blue'}>{card.status}</Tag></Text>
                                                <Text type="secondary">{card.packageDescription} · ${Number(card.estimatedCost ?? 0).toFixed(2)}</Text>
                                                <Button size="small" type="primary" onClick={() => { toggle(); navigate(`/track?code=${encodeURIComponent(card.trackingCode)}`); }}>Track live →</Button>
                                            </Space>
                                        )}
                                        {card.type === 'quote' && (card.candidates ?? []).map((c) => (
                                            <div key={c.candidateId} style={{ display: 'flex', justifyContent: 'space-between', gap: 12, padding: '2px 0' }}>
                                                <span>{c.vehicleType === 'DRONE' ? '🚀' : '🤖'} {c.vehicleType === 'DRONE' ? 'Drone' : 'Robot'}{c.planType === 'OFF_PEAK' ? ' (off-peak)' : ''} · {c.estimatedTimeMinutes} min</span>
                                                <b>${Number(c.estimatedCost).toFixed(2)}</b>
                                            </div>
                                        ))}
                                        {card.type === 'prefill' && (
                                            <Space direction="vertical" size={4}>
                                                <Text strong>📦 {card.itemName || 'Package'}{card.weightKg ? ` · ${card.weightKg} kg` : ''}{card.fragile ? ' · fragile' : ''}</Text>
                                                <Button size="small" type="primary" onClick={() => usePrefill(m.prefill)}>Review in order wizard →</Button>
                                            </Space>
                                        )}
                                    </Card>
                                ))}
                            </div>
                        </div>
                    ))}
                    {busy && <Text type="secondary">Assistant is typing…</Text>}
                </div>
                <Space.Compact style={{ width: '100%', marginTop: 12 }}>
                    <Input ref={inputRef} placeholder="Ask about tracking, prices, or a new order…" onPressEnter={submit} disabled={busy} />
                    <Button type="primary" onClick={submit} loading={busy} style={{ background: '#722ed1', borderColor: '#722ed1' }}>Send</Button>
                </Space.Compact>
            </Drawer>
        </>
    );
}
