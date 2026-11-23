import { Lang } from './models';

/** "1 row" / "2 rows", "1 ligne" / "2 lignes". French treats 0 and 1 as singular. */
export function plural(lang: Lang, n: number, one: string, other: string): string {
  const singular = lang === 'fr' ? n < 2 : n === 1;
  return `${n} ${singular ? one : other}`;
}

const STATUS: Record<Lang, Record<string, string>> = {
  en: { OPEN: 'open', RESOLVED: 'resolved', REJECTED: 'rejected', UPLOADED: 'queued', PROCESSING: 'processing', RECONCILED: 'reconciled', FAILED: 'failed',
    DELIVERED: 'delivered', RETURNED: 'returned', PENDING: 'pending', SHIPPED: 'shipped', CANCELLED: 'cancelled' },
  fr: { OPEN: 'ouverts', RESOLVED: 'résolus', REJECTED: 'rejetés', UPLOADED: 'en file', PROCESSING: 'en traitement', RECONCILED: 'rapprochés', FAILED: 'en échec',
    DELIVERED: 'livrées', RETURNED: 'retournées', PENDING: 'en attente', SHIPPED: 'expédiées', CANCELLED: 'annulées' },
};

function args(json: string): Record<string, unknown> {
  try {
    const parsed = JSON.parse(json) as Record<string, unknown>;
    return Object.fromEntries(Object.entries(parsed).filter(([, v]) => v !== null && v !== ''));
  } catch {
    return {};
  }
}

const mad = (lang: Lang, v: unknown) =>
  `${new Intl.NumberFormat(lang === 'fr' ? 'fr-FR' : 'en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(Number(v))} MAD`;

/**
 * What a tool call did, as one plain sentence for the trace panel:
 * listDisputes({"status":"OPEN","minAmount":400}) -> "Looked up open disputes over 400.00 MAD".
 * The raw call stays available under "Technical details".
 */
export function describeCall(lang: Lang, tool: string, argumentsJson: string): string {
  const a = args(argumentsJson);
  const fr = lang === 'fr';
  const status = (s: unknown) => (typeof s === 'string' ? STATUS[lang][s] ?? s.toLowerCase() : '');
  switch (tool) {
    case 'getSettlementOverview':
      return fr ? 'Consulté la vue d’ensemble de vos encaissements' : 'Looked up your settlement overview';
    case 'getCourierBalances':
      return fr ? 'Consulté le solde de chaque livreur' : 'Looked up what each courier owes you';
    case 'findCourier':
      return a['query'] ? (fr ? `Cherché le livreur « ${a['query']} »` : `Searched for the courier “${a['query']}”`) : fr ? 'Listé vos livreurs' : 'Listed your couriers';
    case 'getCourierBalance':
      return fr ? `Consulté le solde du livreur n°${a['courierId']}` : `Looked up the balance of courier #${a['courierId']}`;
    case 'listDisputes': {
      const over = a['minAmount'] != null ? (fr ? ` de plus de ${mad(lang, a['minAmount'])}` : ` over ${mad(lang, a['minAmount'])}`) : '';
      const st = a['status'] ? status(a['status']) : '';
      return fr ? `Consulté les litiges${st ? ' ' + st : ''}${over}` : `Looked up ${st ? st + ' ' : ''}disputes${over}`;
    }
    case 'getStatementReconciliation':
      return fr ? `Consulté le rapprochement du relevé n°${a['statementId']}` : `Looked up the reconciliation of statement #${a['statementId']}`;
    case 'listStatements': {
      const st = a['status'] ? status(a['status']) : '';
      return fr ? `Consulté vos relevés${st ? ' ' + st : ''}` : `Looked up your ${st ? st + ' ' : ''}statements`;
    }
    case 'searchOrders': {
      const st = a['status'] ? status(a['status']) : '';
      const city = a['city'] ? (fr ? ` à ${a['city']}` : ` in ${a['city']}`) : '';
      const min = a['minAmount'] != null ? (fr ? ` de plus de ${mad(lang, a['minAmount'])}` : ` over ${mad(lang, a['minAmount'])}`) : '';
      return fr ? `Cherché vos commandes${st ? ' ' + st : ''}${city}${min}` : `Searched your ${st ? st + ' ' : ''}orders${city}${min}`;
    }
    case 'searchSettlementDocs':
      return fr ? `Cherché « ${a['query'] ?? ''} » dans le glossaire` : `Searched the glossary for “${a['query'] ?? ''}”`;
    default:
      return tool;
  }
}

/** " · 1 found", " · nothing found", " · not found". */
export function describeResult(lang: Lang, status: string, rows: number): string {
  const fr = lang === 'fr';
  if (status === 'NOT_FOUND') {
    return fr ? 'introuvable' : 'not found';
  }
  if (status !== 'OK') {
    return fr ? 'erreur' : 'failed';
  }
  if (rows === 0) {
    return fr ? 'aucun résultat' : 'nothing found';
  }
  return fr ? plural(lang, rows, 'trouvé', 'trouvés') : `${rows} found`;
}
