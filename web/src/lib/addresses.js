// Demo address book for the order wizard. There is no geocoding service yet,
// and the backend quotes from the station NEAREST THE PICKUP only, so every
// address needs real coordinates. Grouped by the station each one is closest
// to (Station 1 Downtown 500 Howard St, Station 2 Sunset 1900 Irving St,
// Station 3 Mission 2400 Mission St). Shapes follow ContractAddress.
const area = (label, items) => items.map(([line1, zip, lat, lng]) => ({ area: label, line1, city: "San Francisco", zip, lat, lng, addressId: null }));

export const DEMO_ADDRESSES = [
  ...area("Downtown", [
    ["Salesforce Tower, 415 Mission St", "94105", 37.7897, -122.3972],
    ["Ferry Building, 1 Ferry Building", "94111", 37.7955, -122.3937],
    ["Union Square, 333 Post St", "94108", 37.7880, -122.4075],
    ["Moscone Center, 747 Howard St", "94103", 37.7842, -122.4016],
    ["Oracle Park, 24 Willie Mays Plaza", "94107", 37.7786, -122.3893],
    ["Chinatown Gate, 400 Grant Ave", "94108", 37.7908, -122.4058],
  ]),
  ...area("Sunset / Inner Richmond", [
    ["de Young Museum, 50 Hagiwara Tea Garden Dr", "94118", 37.7715, -122.4687],
    ["UCSF Parnassus, 505 Parnassus Ave", "94143", 37.7631, -122.4586],
    ["Irving St & 22nd Ave", "94122", 37.7637, -122.4808],
    ["Stonestown Galleria, 3251 20th Ave", "94132", 37.7281, -122.4760],
  ]),
  ...area("Mission", [
    ["Mission Dolores Park, 19th St & Dolores St", "94114", 37.7596, -122.4269],
    ["24th St Mission BART, 2800 Mission St", "94110", 37.7524, -122.4184],
  ]),
];

export const findAddress = (line1) => DEMO_ADDRESSES.find((a) => a.line1 === line1);

// Options for an antd <Select showSearch>, grouped by area.
export const ADDRESS_OPTIONS = [...new Set(DEMO_ADDRESSES.map((a) => a.area))].map((label) => ({
  label,
  options: DEMO_ADDRESSES.filter((a) => a.area === label).map((a) => ({ label: a.line1, value: a.line1 })),
}));
