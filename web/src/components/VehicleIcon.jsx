import { RobotOutlined, RocketOutlined } from '@ant-design/icons';
// ROBOT has a real icon; DRONE is approximated with a rocket. Swap in a proper
// drone glyph here if the team adds one to the design system.
export function VehicleIcon({ vehicle }) {
    return vehicle === 'ROBOT' ? <RobotOutlined /> : <RocketOutlined />;
}
