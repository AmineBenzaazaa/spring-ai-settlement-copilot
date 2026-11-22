import { Injectable, computed, signal } from '@angular/core';
import { Lang } from './models';

const DICTIONARY = {
  en: {
    switchProduct: 'Switch product',
    suiteDispatch: 'Live parcel operations',
    suiteSettle: 'Courier cash settlement',
    suiteCopilot: 'Ask your settlement data',
    account: 'Account',
    theme: 'Toggle dark mode',
    skip: 'Skip to the conversation',
    signInTo: 'Sign in to',
    heroTitle: 'Ask about your cash. Get the exact figure, and where it came from.',
    heroBody: 'The copilot reads your settlement data with your own permissions: courier balances, statements, disputes. Every amount in an answer is checked against the data before you see it.',
    point1: 'Answers in English or French',
    point2: 'Every amount verified, with links to the rows used',
    point3: 'Says so when the data is not there, instead of guessing',
    sampleHint: 'Sample merchants and a finance user. Password for all: demo-pass-123',
    toolsTitle: 'What I can look up',
    loading: 'Loading…',
    tagline: 'Ask your settlement data anything. Exact numbers, sources, no guessing.',
    signIn: 'Sign in',
    signingIn: 'Signing in…',
    username: 'Username',
    password: 'Password',
    demoAccounts: 'Try it with a sample account',
    loginFailed: 'Wrong username or password.',
    merchant: 'Merchant',
    staff: 'Finance ops',
    askPlaceholder: 'Ask about couriers, statements, disputes…',
    send: 'Send',
    stop: 'Stop',
    signOut: 'Sign out',
    newChat: 'New chat',
    traceTitle: 'Agent trace',
    traceEmpty: 'Tool calls appear here live while the copilot works.',
    rows: 'rows',
    sources: 'Sources',
    verified: 'amounts verified against the data',
    noAmounts: 'No amounts to verify',
    ungrounded: 'Not found in the data:',
    flagged: 'suspicious text neutralized',
    thinking: 'Working…',
    errorGeneric: 'Something went wrong. Please try again.',
    welcome: 'What would you like to know?',
    welcomeSub: 'Answers come from your live settlement data, with your own permissions. Every amount is checked.',
    demoMode: 'Offline demo model',
    demoModeHint: 'Runs offline with a deterministic model: same tools, checks and streaming as the Claude mode.',
    claudeMode: 'Claude',
    tools: 'tools',
    noKey: 'no API key',
    grounded: 'Grounded',
    notGrounded: 'Not grounded',
  },
  fr: {
    switchProduct: 'Changer de produit',
    suiteDispatch: 'Opérations colis en direct',
    suiteSettle: 'Règlement des encaissements',
    suiteCopilot: 'Interrogez vos données',
    account: 'Compte',
    theme: 'Mode sombre',
    skip: 'Aller à la conversation',
    signInTo: 'Connexion à',
    heroTitle: 'Posez vos questions sur vos encaissements. Le chiffre exact, et sa source.',
    heroBody: "Le copilote lit vos données de règlement avec vos propres droits : soldes livreurs, relevés, litiges. Chaque montant d'une réponse est vérifié dans les données avant affichage.",
    point1: 'Réponses en français ou en anglais',
    point2: 'Chaque montant vérifié, avec les lignes sources',
    point3: "Le dit quand la donnée n'existe pas, sans deviner",
    sampleHint: 'Marchands exemples et un compte finance. Mot de passe : demo-pass-123',
    toolsTitle: 'Ce que je peux consulter',
    loading: 'Chargement…',
    tagline: 'Interrogez vos données de règlement. Chiffres exacts, sources, aucune supposition.',
    signIn: 'Se connecter',
    signingIn: 'Connexion…',
    username: "Nom d'utilisateur",
    password: 'Mot de passe',
    demoAccounts: 'Essayez avec un compte exemple',
    loginFailed: 'Identifiant ou mot de passe incorrect.',
    merchant: 'Marchand',
    staff: 'Finance ops',
    askPlaceholder: 'Posez une question sur les livreurs, relevés, litiges…',
    send: 'Envoyer',
    stop: 'Arrêter',
    signOut: 'Déconnexion',
    newChat: 'Nouvelle conversation',
    traceTitle: "Trace de l'agent",
    traceEmpty: "Les appels d'outils s'affichent ici en direct.",
    rows: 'lignes',
    sources: 'Sources',
    verified: 'montants vérifiés dans les données',
    noAmounts: 'Aucun montant à vérifier',
    ungrounded: 'Introuvable dans les données :',
    flagged: 'texte suspect neutralisé',
    thinking: 'Analyse…',
    errorGeneric: 'Une erreur est survenue. Réessayez.',
    welcome: 'Que voulez-vous savoir ?',
    welcomeSub: 'Les réponses viennent de vos données de règlement, avec vos propres droits. Chaque montant est vérifié.',
    demoMode: 'Modèle démo hors ligne',
    demoModeHint: "Déterministe, sans clé d'API. Mêmes outils, garde-fous et streaming que Claude.",
    claudeMode: 'Claude',
    tools: 'outils',
    noKey: "sans clé d'API",
    grounded: 'Vérifié',
    notGrounded: 'Non vérifié',
  },
} as const;

/** Human labels for the enum values the API returns (chart legends, statuses). */
const LABELS: Record<Lang, Record<string, string>> = {
  en: {
    MATCHED: 'Matched', AMOUNT_MISMATCH: 'Amount mismatch', UNKNOWN_TRACKING: 'Unknown tracking', MISSING_FROM_STATEMENT: 'Missing from statement',
    OPEN: 'Open', RESOLVED: 'Resolved', REJECTED: 'Rejected', UPLOADED: 'Queued', PROCESSING: 'Processing', RECONCILED: 'Reconciled', FAILED: 'Failed',
    DELIVERED: 'Delivered', RETURNED: 'Returned', PENDING: 'Pending', SHIPPED: 'Shipped', CANCELLED: 'Cancelled', OK: 'OK', NOT_FOUND: 'Not found', ERROR: 'Error',
  },
  fr: {
    MATCHED: 'Conforme', AMOUNT_MISMATCH: 'Montant différent', UNKNOWN_TRACKING: 'Suivi inconnu', MISSING_FROM_STATEMENT: 'Absent du relevé',
    OPEN: 'Ouvert', RESOLVED: 'Résolu', REJECTED: 'Rejeté', UPLOADED: 'En file', PROCESSING: 'En traitement', RECONCILED: 'Rapproché', FAILED: 'Échec',
    DELIVERED: 'Livré', RETURNED: 'Retourné', PENDING: 'En attente', SHIPPED: 'Expédié', CANCELLED: 'Annulé', OK: 'OK', NOT_FOUND: 'Introuvable', ERROR: 'Erreur',
  },
};

/** What each copilot tool does, in plain words (the raw tool name stays visible in the trace). */
const TOOL_LABELS: Record<Lang, Record<string, string>> = {
  en: {
    getCourierBalances: 'Courier balances', getCourierBalance: 'One courier balance', listStatements: 'Statements',
    getSettlementOverview: 'Settlement overview', getStatementReconciliation: 'Statement reconciliation', findCourier: 'Find a courier',
    listDisputes: 'Disputes', searchOrders: 'Orders', searchSettlementDocs: 'Settlement glossary',
  },
  fr: {
    getCourierBalances: 'Soldes des livreurs', getCourierBalance: "Solde d'un livreur", listStatements: 'Relevés',
    getSettlementOverview: 'Vue d’ensemble', getStatementReconciliation: "Rapprochement d'un relevé", findCourier: 'Trouver un livreur',
    listDisputes: 'Litiges', searchOrders: 'Commandes', searchSettlementDocs: 'Glossaire du règlement',
  },
};

export type I18nKey = keyof (typeof DICTIONARY)['en'];

export const SUGGESTIONS: Record<Lang, string[]> = {
  en: [
    'Which courier owes me the most?',
    'Show open disputes over 400 MAD',
    'Why was statement #1 flagged?',
    'What does AMOUNT_MISMATCH mean?',
    'Give me an overview',
    'Why was statement #12 flagged?',
  ],
  fr: [
    'Quel livreur me doit le plus ?',
    'Montre les litiges ouverts de plus de 400 MAD',
    'Pourquoi le relevé n°1 a-t-il été signalé ?',
    'Que signifie MISSING_FROM_STATEMENT ?',
    'Combien me doit Speedex ?',
    'Quel livreur me devra le plus le mois prochain ?',
  ],
};

const STORAGE_KEY = 'settlement-copilot.lang';

@Injectable({ providedIn: 'root' })
export class I18nService {
  readonly lang = signal<Lang>(initialLang());
  readonly suggestions = computed(() => SUGGESTIONS[this.lang()]);

  t(key: I18nKey): string {
    return DICTIONARY[this.lang()][key];
  }

  /** "AMOUNT_MISMATCH" -> "Amount mismatch"; other text is returned unchanged. */
  label(raw: string): string {
    return LABELS[this.lang()][raw] ?? (/^[A-Z][A-Z_]+$/.test(raw) ? raw.charAt(0) + raw.slice(1).toLowerCase().replaceAll('_', ' ') : raw);
  }

  tool(name: string): string {
    return TOOL_LABELS[this.lang()][name] ?? name;
  }

  setLang(lang: Lang): void {
    this.lang.set(lang);
    try {
      localStorage.setItem(STORAGE_KEY, lang);
    } catch {
      /* ignore */
    }
    document.documentElement.lang = lang;
  }
}

function initialLang(): Lang {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved === 'en' || saved === 'fr') {
      return saved;
    }
  } catch {
    /* ignore */
  }
  return typeof navigator !== 'undefined' && navigator.language?.startsWith('fr') ? 'fr' : 'en';
}
