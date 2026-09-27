// Design system single source of truth. Every antd component reads tokens from
// here, so re-skinning the whole app = editing this one file.
// The wireframes use dark CTAs + blue accents; flip colorPrimary to a dark hex
// here if the team wants the social-ai style black buttons.
export const theme = {
    token: {
        colorPrimary: '#1677ff',
        borderRadius: 8,
        fontSize: 14,
    },
    components: {
        Button: { controlHeight: 40 },
        Card: { borderRadiusLG: 12 },
    },
};
