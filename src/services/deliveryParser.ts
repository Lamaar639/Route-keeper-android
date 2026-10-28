import * as pdfjs from 'pdfjs-dist';
import type { DeliveryStop, DeliveryTimeWindow } from '../types/route';

// Configure PDF.js worker for browser runtime
if (typeof window !== 'undefined') {
  try {
    pdfjs.GlobalWorkerOptions.workerSrc = new URL(
      'pdfjs-dist/build/pdf.worker.min.mjs',
      import.meta.url,
    ).toString();
  } catch {
    pdfjs.GlobalWorkerOptions.workerSrc = `https://cdn.jsdelivr.net/npm/pdfjs-dist@${pdfjs.version || '4.10.38'}/build/pdf.worker.min.mjs`;
  }
}

const PHONE_LABEL = /\b(?:phone|tel|telephone|mobile|contact|cell)(?:\s+number)?\s*[:#-]?\s*(\+?\s*\d[\d\s().-]{7,})/i;
const GENERIC_PHONE = /\b(\+?44\s*\d[\d\s().-]{8,}|\b0[1278]\d{2}[\s.-]?\d{3}[\s.-]?\d{3,4}\b|\b07\d{3}[\s.-]?\d{4,6}\b|\+?\d{1,3}[\s.-]?\(?\d{2,4}\)?[\s.-]?\d{3,4}[\s.-]?\d{3,4})\b/;
const ORDER_ID = /\b(?:order\s*id|order\s*#|ref\s*#|job\s*#|invoice\s*#)\s*[:#-]?\s*([a-z0-9#-]+)|\(\s*order\s*id\s*:\s*([^)]+)\)/i;
const UK_POSTCODE = /\b([A-Z]{1,2}[0-9][A-Z0-9]?\s*[0-9][A-Z]{2})\b/i;
const ROAD_KEYWORDS = /\b(?:street|st|road|rd|avenue|ave|lane|ln|drive|dr|close|cl|way|court|ct|crescent|cres|terrace|square|sq|hill|walk|park|place|pl|gardens|gdns|boulevard|blvd|broadway|row|gate|wharf|quay)\b/i;
const DROP_NUMBER_REGEX = /(?:drop|stop|delivery|job|task)\s*(?:#|no\.?|num\.?)?\s*(\d+)|^(\d+)[.:)]\s+/i;

function makeId(): string {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}

export function cleanName(value: string): string {
  return value
    .replace(ORDER_ID, '')
    .replace(/\b(?:stop|drop|delivery|job|task)\s*#?\s*\d+\s*[:.-]?\s*/gi, '')
    .replace(/\bnew\s+customer\b/gi, '')
    .replace(/\b(?:customer|client|recipient|name)\s*:\s*/i, '')
    .replace(/[★☆•▪■|]/g, '')
    .replace(/\s+/g, ' ')
    .replace(/^[\s\d.-]+|[\s,;|]+$/g, '')
    .trim();
}

export function cleanPhone(value: string): string {
  const compact = value.trim().replace(/[^\d+]/g, '');
  return compact.startsWith('+')
    ? `+${compact.slice(1).replace(/\+/g, '')}`
    : compact.replace(/\+/g, '');
}

export function cleanAddress(value: string): string {
  return value
    .replace(/^\s*(?:address|delivery\s+address|location)\s*[:#-]?\s*/i, '')
    .replace(/\b(?:time\s*slot|delivery\s*window|slot|window|eta|between|deliver\s+between)\s*[:#-]?\s*[^,;|\n]+/gi, '')
    .replace(/\b\d{1,2}[:.]\d{2}\s*(?:am|pm)?\s*(?:[-–—~]|to|and)\s*\d{1,2}[:.]\d{2}\s*(?:am|pm)?/gi, '')
    .replace(/\b(?:morning|afternoon|evening)\s*(?:slot|window)?\b/gi, '')
    .replace(/\s+(?:drop-off|arrival:|departure:|delivery time:|driver notes:|instructions:).*/i, '')
    .replace(/\(\s*\)/g, '')
    .replace(/\s+/g, ' ')
    .replace(/^[\s,;|()]+|[\s,;|()]+$/g, '')
    .trim();
}

export function isAddressLine(line: string): boolean {
  if (!line || line.length < 5) return false;
  if (UK_POSTCODE.test(line)) return true;
  if (ROAD_KEYWORDS.test(line)) return true;
  if (/^\d{1,4}[a-z]?\s+[a-z]+/i.test(line.trim())) return true;
  return false;
}

function isNonAddressLine(value: string): boolean {
  return (
    !value ||
    /^(?:route\s*manifest|route\s*summary|route\s*id:|status:|deliveries:|driver:|vehicle:|dispatch:|return:|break:|these\s*orders\s*contain|drop-off:|arrival:|departure:|delivery\s*time:|driver\s*notes:|instructions:|time\s*slot:|delivery\s*window:|date:|total\s*packages)/i.test(
      value,
    ) ||
    /^\d+$/.test(value)
  );
}

// Robust, comprehensive time slot parser to know what time you should be delivering between
export function parseTimeSlot(text: string): DeliveryTimeWindow | undefined {
  if (!text) return undefined;

  // 1. Strip dates (e.g. 12/10/2026 or 2026-10-12) so date numbers never block or confuse time slot matching
  const clean = text
    .replace(/\b\d{1,2}[/-]\d{1,2}[/-]\d{2,4}\b/g, ' ')
    .replace(/\b\d{4}[/-]\d{1,2}[/-]\d{1,2}\b/g, ' ')
    .trim();

  // 2. Named delivery windows
  if (/\b(?:morning|am\s*slot|am\s*window|am\s*delivery)\b/i.test(clean) && !/\d{1,2}[:.]\d{2}/.test(clean)) {
    return {
      startTime: '09:00',
      endTime: '13:00',
      durationHours: 4,
      label: '09:00 – 13:00 (4h)',
    };
  }
  if (/\b(?:afternoon|pm\s*slot|pm\s*window|pm\s*delivery)\b/i.test(clean) && !/\d{1,2}[:.]\d{2}/.test(clean)) {
    return {
      startTime: '13:00',
      endTime: '17:00',
      durationHours: 4,
      label: '13:00 – 17:00 (4h)',
    };
  }
  if (/\b(?:evening|evening\s*slot)\b/i.test(clean) && !/\d{1,2}[:.]\d{2}/.test(clean)) {
    return {
      startTime: '17:00',
      endTime: '21:00',
      durationHours: 4,
      label: '17:00 – 21:00 (4h)',
    };
  }

  // 3. Multi-pattern time range detection
  // Matches:
  // - "09:00 - 10:00", "09:00-10:00", "9:00 - 10:00", "10:00 to 11:00", "between 10:00 and 11:00"
  // - "10.00 - 11.00", "10.00-11.00"
  // - "0900 - 1000", "0900-1000", "1300 - 1700"
  // - "1pm - 5pm", "10am - 11am", "9am to 1pm", "1 - 5pm", "10 - 11am"
  // - "Deliver between: 09:00 - 10:00", "Time Slot: 10:00 - 11:00", "Window: 13:00 - 17:00"
  const rangeRegex = /(?:(?:slot|window|time|delivery|between|deliver\s*between|eta|expected|target|period|booked)\s*[:#-]?\s*)?(\d{1,2}[:.]\d{2}\s*(?:am|pm)?|\d{4}|\d{1,2}\s*(?:am|pm)?)\s*(?:[-–—~]|to|and|until)\s*(\d{1,2}[:.]\d{2}\s*(?:am|pm)?|\d{4}|\d{1,2}\s*(?:am|pm)?)/i;

  let match = clean.match(rangeRegex);

  // Simplified "1 - 5pm" or "10 - 11am" where only the second time has am/pm
  if (!match) {
    match = clean.match(/(?:slot|window|time|delivery|between|deliver\s*between|eta|booked)?\s*[:#-]?\s*(\d{1,2})\s*(?:[-–—~]|to|and)\s*(\d{1,2}\s*(?:am|pm))/i);
  }

  // Single deadline: "Deliver by 12:00" or "Before 12:00" -> 09:00 - 12:00
  if (!match) {
    const deadlineMatch = clean.match(/(?:deliver\s+by|before|by|until)\s*[:#-]?\s*(\d{1,2}[:.]\d{2}\s*(?:am|pm)?|\d{1,2}\s*(?:am|pm))/i);
    if (deadlineMatch) {
      match = [deadlineMatch[0], '09:00', deadlineMatch[1]];
    }
  }

  // Single start: "After 13:00" or "From 13:00" -> 13:00 - 17:00
  if (!match) {
    const afterMatch = clean.match(/(?:deliver\s+after|after|from)\s*[:#-]?\s*(\d{1,2}[:.]\d{2}\s*(?:am|pm)?|\d{1,2}\s*(?:am|pm))/i);
    if (afterMatch) {
      match = [afterMatch[0], afterMatch[1], '17:00'];
    }
  }

  if (!match) return undefined;

  const rawStart = match[1].trim();
  const rawEnd = match[2].trim();

  const endHasPm = /pm/i.test(rawEnd);
  const startNum = parseInt(rawStart, 10);
  const defaultStartPm = endHasPm && startNum >= 1 && startNum <= 7;

  const normalizeHourMin = (raw: string, defaultPm = false): string | null => {
    let s = raw.toLowerCase().trim();
    const isPm = /pm/.test(s) || (defaultPm && !/am/.test(s));
    const isAm = /am/.test(s);
    s = s.replace(/[^\d:.]/g, '');

    let h = 0;
    let m = 0;

    if (s.includes(':')) {
      const parts = s.split(':');
      h = parseInt(parts[0], 10);
      m = parseInt(parts[1], 10) || 0;
    } else if (s.includes('.')) {
      const parts = s.split('.');
      h = parseInt(parts[0], 10);
      m = parseInt(parts[1], 10) || 0;
    } else if (s.length === 4 && /^\d{4}$/.test(s)) {
      h = parseInt(s.slice(0, 2), 10);
      m = parseInt(s.slice(2), 10);
    } else {
      h = parseInt(s, 10);
      m = 0;
    }

    if (isNaN(h) || h < 0 || h > 23 || m < 0 || m > 59) return null;
    if (isPm && h < 12) h += 12;
    if (isAm && h === 12) h = 0;

    return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`;
  };

  const startTime = normalizeHourMin(rawStart, defaultStartPm);
  const endTime = normalizeHourMin(rawEnd, false);

  if (!startTime || !endTime) return undefined;

  const [sh, sm] = startTime.split(':').map(Number);
  const [eh, em] = endTime.split(':').map(Number);
  let diffMinutes = eh * 60 + em - (sh * 60 + sm);
  if (diffMinutes < 0) diffMinutes += 24 * 60;
  const diffHours = diffMinutes / 60;

  if (diffHours < 0.4 || diffHours > 9) return undefined;

  let durationHours: 1 | 4 | 'custom' = 'custom';
  if (Math.abs(diffHours - 1) <= 0.35) durationHours = 1;
  else if (Math.abs(diffHours - 4) <= 0.65) durationHours = 4;

  const label =
    durationHours === 1
      ? `${startTime} – ${endTime} (1h)`
      : durationHours === 4
        ? `${startTime} – ${endTime} (4h)`
        : `${startTime} – ${endTime}`;

  return {
    startTime,
    endTime,
    durationHours,
    label,
  };
}

export function makeStop(
  name: string,
  phone: string,
  address: string,
  dropNumber: number | string = 1,
  orderId?: string,
  timeWindow?: DeliveryTimeWindow,
): DeliveryStop | null {
  const safeName = cleanName(name);
  let safePhone = cleanPhone(phone);
  const safeAddress = cleanAddress(address);

  // If timeWindow wasn't explicitly passed, extract from address or name
  let extractedWindow = timeWindow;
  if (!extractedWindow) {
    extractedWindow = parseTimeSlot(address) || parseTimeSlot(name);
  }

  // If phone is too short, keep empty string rather than discarding the entire stop
  if (safePhone && safePhone.replace(/\D/g, '').length < 7) {
    safePhone = '';
  }

  if (!safeName || !safeAddress) {
    return null;
  }

  const numericSeq = typeof dropNumber === 'number' ? dropNumber : parseInt(String(dropNumber), 10) || 1;

  return {
    id: makeId(),
    name: safeName,
    phone: safePhone,
    address: safeAddress,
    orderId: orderId?.trim(),
    sourceSequence: numericSeq,
    dropNumber: dropNumber,
    latitude: null,
    longitude: null,
    geocodeStatus: 'pending',
    delivered: false,
    timeWindow: extractedWindow,
    serviceTimeMinutes: 6.5, // 5-8 min dwell time
  };
}

// Strategy 1: Dispatch Delivery Summary / Manifest Parser
// Handles modern logistics manifests (supermarkets, couriers, dispatch sheets)
// Pattern: Customer line with Order ID, phone number, sequential drop number line, address line,
// and delivery timeslot (e.g. 07:00 - 08:00 or multi-line 07:00 ... - ... 08:00)
function parseDispatchDeliverySummary(lines: string[]): DeliveryStop[] {
  const stops: DeliveryStop[] = [];
  const blockStartIndices: number[] = [];

  for (let i = 0; i < lines.length; i++) {
    const l = lines[i].trim();
    if (
      /^(?:Route\s*Summary|Route\s*ID|Dispatch:|Return:|Status:|Deliveries:|Driver:|Vehicle:|These\s*orders\s*contain|\*\*\*)/i.test(
        l,
      )
    ) {
      continue;
    }
    // Customer block signature
    if (
      /\(Order ID:\s*\d+\)/i.test(l) ||
      (/\bPhone:\s*(\+?\d[\d\s().-]{7,})/i.test(l) &&
        !/^(?:Route|Dispatch|Driver|Vehicle|Break|Rest)/i.test(l)) ||
      /^(?:Customer(?:\s*Name)?|Recipient|Client)\s*:/i.test(l)
    ) {
      blockStartIndices.push(i);
    }
  }

  if (blockStartIndices.length === 0) return [];

  for (let b = 0; b < blockStartIndices.length; b++) {
    const start = blockStartIndices[b];
    const end = b + 1 < blockStartIndices.length ? blockStartIndices[b + 1] : lines.length;
    const block = lines.slice(start, end).map((l) => l.trim()).filter(Boolean);
    if (block.length === 0) continue;

    const header = block[0];
    let name = '';
    let phone = '';
    let orderId: string | undefined;
    let dropNumber: number | string = b + 1;
    let address = '';
    let startTime = '';
    let endTime = '';

    const orderMatch =
      header.match(/\(Order ID:\s*([^)]+)\)/i) ||
      block.join(' ').match(/\(Order ID:\s*([^)]+)\)/i);
    if (orderMatch) orderId = orderMatch[1].trim();

    const phoneMatch =
      header.match(/Phone:\s*([+\d\s().-]+)/i) ||
      block.join(' ').match(/Phone:\s*([+\d\s().-]+)/i);
    if (phoneMatch) {
      phone = cleanPhone(phoneMatch[1].trim());
    }

    name = cleanName(
      header
        .replace(/\(Order ID:[^)]*\)/i, '')
        .replace(/\bNEW CUSTOMER\b.*/i, '')
        .replace(/\bPhone:.*$/i, '')
        .replace(/^(?:Customer(?:\s*Name)?|Recipient|Client)\s*:\s*/i, '')
        .replace(/^(?:Drop|Stop|Delivery)\s*#?\d+\s*[:.-]?\s*/i, '')
        .replace(/\t+/g, ' '),
    );

    for (let i = 1; i < block.length; i++) {
      const line = block[i];
      if (/^(?:BREAK|REST)\b/i.test(line)) continue;

      if (/^\d{1,3}$/.test(line) && (i === 1 || i === 2) && !address) {
        dropNumber = parseInt(line, 10);
        continue;
      }
      const dropM = line.match(/^(?:Drop|Stop|Delivery)\s*#?(\d+)/i);
      if (dropM) {
        dropNumber = parseInt(dropM[1], 10);
      }

      // Phone detection on subsequent lines
      if (!phone) {
        const pM =
          line.match(/Phone(?:\s*Number)?\s*[:#-]?\s*([+\d\s().-]+)/i) ||
          line.match(PHONE_LABEL) ||
          line.match(GENERIC_PHONE);
        if (pM) {
          const rawP = pM[1] || pM[0];
          if (rawP.replace(/\D/g, '').length >= 7) {
            phone = cleanPhone(rawP);
          }
        }
      }

      // Order ID detection on subsequent lines
      if (!orderId) {
        const oM = line.match(/\(Order ID:\s*([^)]+)\)/i) || line.match(ORDER_ID);
        if (oM) {
          orderId = (oM[1] || oM[2] || oM[0]).trim();
        }
      }

      const hasPostcode = UK_POSTCODE.test(line);
      const hasRoad = ROAD_KEYWORDS.test(line);
      if (
        !address &&
        !line.startsWith('Drop-off') &&
        !/^\d{1,2}:\d{2}/.test(line) &&
        (hasPostcode || hasRoad || (i <= 3 && line.includes(',')))
      ) {
        address = cleanAddress(
          line
            .replace(/\t+/g, ' ')
            .replace(/\s+(?:Drop-off|Arrival:|Departure:|Delivery time:).*/i, ''),
        );
      }

      // Timeslot detection
      const rangeMatch = line.match(/(\d{1,2}:\d{2})\s*(?:[-–—~]|to)\s*(\d{1,2}:\d{2})/);
      if (rangeMatch && !/Arrival|Departure/i.test(line)) {
        startTime = rangeMatch[1];
        endTime = rangeMatch[2];
      } else {
        const timeAtStart = line.match(/^(\d{1,2}:\d{2})\b/);
        if (timeAtStart && !/^(?:Arrival|Departure|Delivery time)/i.test(line)) {
          if (!startTime) {
            startTime = timeAtStart[1];
          } else if (!endTime && timeAtStart[1] !== startTime) {
            endTime = timeAtStart[1];
          }
        }
      }
    }

    let timeWindow: DeliveryTimeWindow | undefined;
    if (startTime && endTime) {
      const [sh, sm] = startTime.split(':').map(Number);
      const [eh, em] = endTime.split(':').map(Number);
      let diffMinutes = eh * 60 + em - (sh * 60 + sm);
      if (diffMinutes < 0) diffMinutes += 24 * 60;
      const durHours = Math.round(diffMinutes / 60) || 1;
      timeWindow = {
        startTime,
        endTime,
        durationHours: durHours === 1 ? 1 : durHours === 4 ? 4 : 'custom',
        label: `${startTime} – ${endTime} (${durHours}h)`,
      };
    }

    if (name && address) {
      const stop = makeStop(name, phone, address, dropNumber, orderId, timeWindow);
      if (stop) stops.push(stop);
    }
  }

  return stops;
}

// Strategy 2: Section / Customer Blocks Parser
// Splits on customer block headers (Customer Name:, Drop 1, Stop 1, Delivery #1, etc.)
function parseCustomerBlocks(text: string): DeliveryStop[] {
  const cleanText = text.replace(/\r\n/g, '\n').replace(/\u0000/g, '');

  let rawBlocks = cleanText
    .split(/(?:^|\n)(?=(?:customer\s*name|client\s*name|recipient|drop\s*#?\d+|stop\s*#?\d+|delivery\s*#?\d+|\d+\.\s+[A-Z]))/i)
    .map((b) => b.trim())
    .filter((b) => b.length > 15 && !/^(?:route\s*manifest|date:|total\s*packages|drop\s*\|)/i.test(b) && !/customer\s*name\s*\|/i.test(b));

  if (rawBlocks.length <= 1) {
    // Try double-newline blocks
    rawBlocks = cleanText
      .split(/\n\s*\n+/)
      .map((b) => b.trim())
      .filter((b) => b.length > 15 && !/^(?:route\s*manifest|date:|total\s*packages)/i.test(b));
  }

  if (rawBlocks.length === 0) return [];

  const stops: DeliveryStop[] = [];
  let seq = 1;

  for (const block of rawBlocks) {
    const lines = block.split('\n').map((l) => l.trim()).filter(Boolean);
    if (lines.length === 0) continue;

    // 1. Delivery/Drop Number
    let dropNumber: number | string = seq;
    const dropMatch = block.match(DROP_NUMBER_REGEX);
    if (dropMatch) {
      dropNumber = parseInt(dropMatch[1] || dropMatch[2], 10);
    }

    // 2. Order ID
    const orderId = block.match(ORDER_ID)?.[1] || block.match(ORDER_ID)?.[2];

    // 3. Time slot (Deliver between)
    const timeWindow = parseTimeSlot(block);

    // 4. Name, Phone, Address scanning
    let name = '';
    let address = '';
    let phone = '';

    for (const line of lines) {
      // Name
      if (!name) {
        const nameMatch = line.match(/(?:customer|client|recipient|name)\s*:\s*([^\n|;]+)/i);
        if (nameMatch) name = cleanName(nameMatch[1]);
      }

      // Address
      if (!address) {
        const addrMatch = line.match(/(?:delivery\s*)?address\s*:\s*([^\n|;]+)/i);
        if (addrMatch) {
          address = cleanAddress(addrMatch[1]);
        } else if (UK_POSTCODE.test(line) || ROAD_KEYWORDS.test(line)) {
          address = cleanAddress(line);
        }
      }

      // Phone
      if (!phone) {
        const phoneMatch = line.match(PHONE_LABEL) || line.match(GENERIC_PHONE);
        if (phoneMatch) {
          const matchedPhone = phoneMatch[1] || phoneMatch[0];
          if (matchedPhone && matchedPhone.replace(/\D/g, '').length >= 7) {
            phone = cleanPhone(matchedPhone);
          }
        }
      }
    }

    // If no labeled name found, pick the first line that isn't a drop header, address or phone
    if (!name) {
      for (const line of lines) {
        if (/^(?:drop|stop|delivery)\s*#?\d+$/i.test(line)) continue;
        if (line.includes(':') || line === address || line === phone) continue;
        if (UK_POSTCODE.test(line) || ROAD_KEYWORDS.test(line)) continue;
        if (/\d{4,}/.test(line)) continue;
        name = cleanName(line.replace(/^[0-9]+[.:)]\s*/, ''));
        if (name) break;
      }
    }

    if (name && address) {
      const stop = makeStop(name, phone, address, dropNumber, orderId, timeWindow);
      if (stop) {
        stops.push(stop);
        seq += 1;
      }
    }
  }

  return stops;
}

// Strategy 2: Tabular / Column Separator Parser
// Handles pipe |, tab \t, semicolon ;, or multi-column layout (2+ spaces)
function parseTabularRows(lines: string[]): DeliveryStop[] {
  const stops: DeliveryStop[] = [];

  for (const line of lines) {
    if (!line.includes('|') && !line.includes('\t') && !line.includes(';') && !line.includes('  ')) continue;
    if (/^(?:drop|stop|no|#)\s*\|/i.test(line)) continue; // skip header row

    const cols = line.split(/[|\t;]|\s{2,}/).map((c) => c.trim()).filter(Boolean);
    if (cols.length < 3) continue;

    let dropNumber: number | string = stops.length + 1;
    let name = '';
    let phone = '';
    let address = '';
    let timeWindow: DeliveryTimeWindow | undefined;
    let orderId: string | undefined;

    for (const col of cols) {
      // Time Slot
      if (!timeWindow) {
        const slot = parseTimeSlot(col);
        if (slot) {
          timeWindow = slot;
          continue;
        }
      }

      // Phone
      if (!phone) {
        const phoneMatch = col.match(PHONE_LABEL) || col.match(GENERIC_PHONE);
        if (phoneMatch) {
          const p = phoneMatch[1] || phoneMatch[0];
          if (p && p.replace(/\D/g, '').length >= 7) {
            phone = cleanPhone(p);
            continue;
          }
        }
      }

      // Order ID
      if (!orderId) {
        const orderMatch = col.match(ORDER_ID);
        if (orderMatch) {
          orderId = orderMatch[1] || orderMatch[2];
        }
      }

      // Address
      if (!address && isAddressLine(col)) {
        address = cleanAddress(col);
        const inlineSlot = parseTimeSlot(col);
        if (inlineSlot && !timeWindow) {
          timeWindow = inlineSlot;
        }
        continue;
      }

      // Drop Number
      if (/^\d+$/.test(col) && parseInt(col, 10) < 500) {
        dropNumber = parseInt(col, 10);
        continue;
      }

      // Name
      if (!name && col.length < 40 && !/^\d+$/.test(col) && !/^(?:stop|drop|order)\s*\d+/i.test(col)) {
        name = cleanName(col);
      }
    }

    if (name && address) {
      const stop = makeStop(name, phone, address, dropNumber, orderId, timeWindow);
      if (stop) stops.push(stop);
    }
  }

  return stops;
}

// Strategy 3: Sliding Lookahead Parser (Anchored by phone or address)
function parseSlidingWindowRecords(lines: string[]): DeliveryStop[] {
  const stops: DeliveryStop[] = [];

  for (let index = 0; index < lines.length; index += 1) {
    const line = lines[index];
    const phoneMatch = line.match(PHONE_LABEL) || line.match(GENERIC_PHONE);
    if (!phoneMatch) continue;

    const matchedPhone = phoneMatch[1] || phoneMatch[0];
    if (matchedPhone.replace(/\D/g, '').length < 7) continue;

    let dropNumber: number | string = stops.length + 1;
    let name = '';
    const phoneStart = line.search(PHONE_LABEL);
    if (phoneStart > 0) {
      name = cleanName(line.slice(0, phoneStart));
    }
    if (!name && index > 0) {
      name = cleanName(lines[index - 1]);
    }
    if (!name && index > 1) {
      name = cleanName(lines[index - 2]);
    }

    const orderId = line.match(ORDER_ID)?.[1] || line.match(ORDER_ID)?.[2];
    let address = '';
    let timeWindow: DeliveryTimeWindow | undefined;

    const windowStart = Math.max(0, index - 2);
    const windowEnd = Math.min(lines.length, index + 6);

    for (let i = windowStart; i < windowEnd; i++) {
      const candidate = lines[i];

      const dropM = candidate.match(DROP_NUMBER_REGEX);
      if (dropM) {
        dropNumber = parseInt(dropM[1] || dropM[2], 10);
      }

      if (!timeWindow) {
        const slot = parseTimeSlot(candidate);
        if (slot) {
          timeWindow = slot;
        }
      }

      if (!address && isAddressLine(candidate)) {
        address = cleanAddress(candidate);
      }
    }

    if (name && address) {
      const stop = makeStop(name, cleanPhone(matchedPhone), address, dropNumber, orderId, timeWindow);
      if (stop) stops.push(stop);
    }
  }

  return stops;
}

// Strategy 4: Freeform Single-Line Records
// Handles lines like: "1. Arthur Pendelton - 10 Downing Street, London, SW1A 2AA - 07946 0192 - 09:00 - 10:00"
function parseFreeformRowRecords(lines: string[]): DeliveryStop[] {
  const stops: DeliveryStop[] = [];

  for (const line of lines) {
    if (line.length < 15 || isNonAddressLine(line)) continue;

    // 1. Drop Number
    let dropNumber: number | string = stops.length + 1;
    const dropMatch = line.match(DROP_NUMBER_REGEX);
    if (dropMatch) {
      dropNumber = parseInt(dropMatch[1] || dropMatch[2], 10);
    }

    // 2. Time Slot
    const timeWindow = parseTimeSlot(line);

    // 3. Phone
    const phoneMatch = line.match(PHONE_LABEL) || line.match(GENERIC_PHONE);
    const phone = phoneMatch ? cleanPhone(phoneMatch[1] || phoneMatch[0]) : '';

    // 4. Order ID
    const orderMatch = line.match(ORDER_ID);
    const orderId = orderMatch ? orderMatch[1] || orderMatch[2] : undefined;

    // 5. Strip out time slot and phone to locate name and address
    let working = line;
    if (timeWindow) {
      working = working
        .replace(/\b(?:time\s*slot|delivery\s*window|slot|window|eta|between|deliver\s+between)\s*[:#-]?\s*[^,;|\n]+/gi, ' ')
        .replace(/\b\d{1,2}[:.]\d{2}\s*(?:am|pm)?\s*(?:[-–—~]|to|and)\s*\d{1,2}[:.]\d{2}\s*(?:am|pm)?/gi, ' ');
    }
    if (phoneMatch) {
      working = working.replace(phoneMatch[0], ' ');
    }
    if (orderMatch) {
      working = working.replace(orderMatch[0], ' ');
    }

    working = working.replace(/^\s*(?:stop|drop|task)?\s*#?\s*\d+[:.)-]?\s+/i, '').trim();

    const postcodeMatch = working.match(UK_POSTCODE);
    const roadKeywordMatch = working.match(ROAD_KEYWORDS);

    let name = '';
    let address = '';

    if (postcodeMatch || roadKeywordMatch) {
      const addressStartIndex = working.search(/\b\d{1,4}[a-z]?\s+[a-z]+/i);
      if (addressStartIndex > 0) {
        name = cleanName(working.slice(0, addressStartIndex));
        address = cleanAddress(working.slice(addressStartIndex));
      } else {
        const commaIdx = working.indexOf(',');
        if (commaIdx > 2) {
          name = cleanName(working.slice(0, commaIdx));
          address = cleanAddress(working.slice(commaIdx + 1));
        }
      }
    }

    if (name && address) {
      const stop = makeStop(name, phone, address, dropNumber, orderId, timeWindow);
      if (stop) stops.push(stop);
    }
  }

  return stops;
}

export function parseDeliveryListText(text: string): DeliveryStop[] {
  if (!text || !text.trim()) return [];

  const normalized = text
    .replace(/\u0000/g, '')
    .replace(/\f/g, '\n')
    .replace(/\r\n?/g, '\n');

  const lines = normalized
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean);

  let parsed: DeliveryStop[] = [];

  // Strategy 1: Dispatch Delivery Summary (supermarket and courier manifests with customer headers, order ID, phone, drop number, address, and timeslots)
  parsed = parseDispatchDeliverySummary(lines);

  // Strategy 2: Labeled Customer blocks (Customer Name:, Address:, Time Slot:, Drop 1, etc.)
  if (parsed.length === 0) {
    parsed = parseCustomerBlocks(normalized);
  }

  // Strategy 3: Tabular pipe rows (Explicit delimiter '|' tables)
  if (parsed.length === 0) {
    const hasPipes = lines.filter((l) => l.includes('|')).length >= 2;
    if (hasPipes) {
      parsed = parseTabularRows(lines);
    }
  }

  // Strategy 4: Sliding window around phone or postcode anchors
  if (parsed.length === 0) {
    parsed = parseSlidingWindowRecords(lines);
  }

  // Strategy 5: Freeform single-line rows
  if (parsed.length === 0) {
    parsed = parseFreeformRowRecords(lines);
  }

  // Strategy 6: Tabular multi-column fallback
  if (parsed.length === 0) {
    parsed = parseTabularRows(lines);
  }

  // De-duplicate stops while preserving extracted drop numbers and delivery time windows
  const seen = new Set<string>();
  return parsed.filter((stop) => {
    const key = `${stop.phone || ''}|${stop.address.toLowerCase()}`;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

// PDF text extractor preserving table column separations
function pdfItemsToLines(items: unknown[]): string[] {
  const positioned = items
    .filter((item): item is { str: string; transform: number[] } => {
      return (
        typeof item === 'object' &&
        item !== null &&
        'str' in item &&
        typeof (item as any).str === 'string' &&
        'transform' in item &&
        Array.isArray((item as any).transform)
      );
    })
    .map((item) => ({
      x: item.transform[4] ?? 0,
      y: item.transform[5] ?? 0,
      text: item.str,
    }))
    .filter((item) => item.text.trim())
    .sort((a, b) => b.y - a.y || a.x - b.x);

  const rows: Array<{ y: number; items: typeof positioned }> = [];
  for (const item of positioned) {
    const row = rows[rows.length - 1];
    // Vertical alignment tolerance for table rows (4px)
    if (row && Math.abs(row.y - item.y) <= 4.0) {
      row.items.push(item);
      row.y = (row.y * (row.items.length - 1) + item.y) / row.items.length;
    } else {
      rows.push({ y: item.y, items: [item] });
    }
  }

  return rows.map((row) => {
    const sorted = row.items.sort((a, b) => a.x - b.x);
    let lineStr = '';
    let lastX = -1;
    let lastLen = 0;

    for (const it of sorted) {
      if (lastX >= 0) {
        const estPrevWidth = Math.max(10, lastLen * 5.2);
        const gap = it.x - (lastX + estPrevWidth);
        if (gap > 13) {
          lineStr += '\t';
        } else {
          lineStr += ' ';
        }
      }
      lineStr += it.text;
      lastX = it.x;
      lastLen = it.text.length;
    }

    return lineStr.trim();
  });
}

export async function readDeliveryFile(file: File): Promise<string> {
  const isPdf = file.type.includes('pdf') || file.name.toLowerCase().endsWith('.pdf');
  if (isPdf) {
    const arrayBuffer = await file.arrayBuffer();
    const bytes = new Uint8Array(arrayBuffer);
    const loadingTask = pdfjs.getDocument({
      data: bytes,
      useSystemFonts: true,
      isEvalSupported: false,
    });
    const document = await loadingTask.promise;
    try {
      const pages: string[] = [];
      for (let pageNumber = 1; pageNumber <= document.numPages; pageNumber += 1) {
        const page = await document.getPage(pageNumber);
        const content = await page.getTextContent();
        pages.push(pdfItemsToLines(content.items).join('\n'));
      }
      const fullText = pages.join('\n');
      if (!fullText.trim()) {
        throw new Error(
          'This PDF appears to contain scanned images rather than selectable text. Please choose a text-based PDF or copy/paste the delivery summary.',
        );
      }
      return fullText;
    } finally {
      await loadingTask.destroy();
    }
  }
  return file.text();
}

export const SAMPLE_DELIVERY_NOTE_TEXT = `ROUTE MANIFEST: CENTRAL METRO DISPATCH
Date: 12 Oct 2026 | Route: North-West Express
--------------------------------------------------
Customer Name: Arthur Pendelton
Phone Number: +44 20 7946 0192 (Order ID: ORD-9921)
Address: 10 Downing Street, London, SW1A 2AA
Time Slot: 09:00 - 10:00 (1h slot)

Customer Name: Beatrice Higgins
Phone Number: +44 20 7946 0843 (Order ID: ORD-9922)
Address: 221B Baker Street, Marylebone, London, NW1 6XE
Time Slot: 10:00 - 11:00 (1h slot)

Customer Name: Charles Montgomery
Phone Number: +44 20 7946 0521 (Order ID: ORD-9923)
Address: Russell Square, Bloomsbury, London, WC1B 5EH
Time Slot: 11:00 - 12:00 (1h slot)

Customer Name: Eleanor Vance
Phone Number: +44 20 7946 0317 (Order ID: ORD-9924)
Address: Covent Garden Piazza, London, WC2E 8HD
Time Slot: 13:00 - 17:00 (4h slot)

Customer Name: David Sterling
Phone Number: +44 20 7946 0462 (Order ID: ORD-9925)
Address: Trafalgar Square, Westminster, London, WC2N 5DN
Time Slot: 13:00 - 17:00 (4h slot)

Customer Name: Fiona Gallagher
Phone Number: +44 20 7946 0779 (Order ID: ORD-9926)
Address: Borough Market, Southwark, London, SE1 1TL
Time Slot: 17:00 - 18:00 (1h slot)
--------------------------------------------------
Total Packages: 6 | Delivery Window: 08:00 - 18:00`;
