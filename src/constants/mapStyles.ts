export const MAP_MARKER_COLORS = [
  { label: 'Forest', value: '#205448' },
  { label: 'Slate', value: '#415C66' },
  { label: 'Terracotta', value: '#B75D43' },
  { label: 'Ink', value: '#28384D' },
];

export const MAP_LINE_COLORS = [
  { label: 'Citrus', value: '#D3E879' },
  { label: 'Teal', value: '#55B6A1' },
  { label: 'Coral', value: '#DF8A67' },
  { label: 'Blue', value: '#75A9DB' },
];

export const DEFAULT_MAP_PREFERENCES = {
  markerColor: MAP_MARKER_COLORS[0].value,
  lineColor: MAP_LINE_COLORS[0].value,
  lineWidth: 4,
  showAllTimeMap: true,
  defaultNavApp: 'ask' as const,
};

