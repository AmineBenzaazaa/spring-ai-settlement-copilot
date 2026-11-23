import { Injectable, computed, signal } from '@angular/core';
import { Lang } from './models';
import { plural } from './describe';

const DICTIONARY = {
  en: {
    techDetails: 'Technical details',
    noRecords: 'No matching records',
    noRecordsHint: 'No matching records in your data, so there were no amounts to verify.',
    responseTime: 'Response time:',
    canSee: 'What I can see',
    canSeeNote: 'Only your own data: I use your permissions, never more.',
    seeBalances: 'Courier balances',
    seeStatements: 'Statements and their reconciliation',
    seeDisputes: 'Disputes',
    seeOrders: 'Orders',
    seeGlossary: 'The settlement glossary',
    starters: 'Start with a question',
    howItWorks: 'How it works',
    close: 'Close',
    topicCouriers: 'Couriers',
    topicStatements: 'Statements',
    topicDisputes: 'Disputes',
    topicOrders: 'Orders',
    topicGlossary: 'Definitions',
    moreInfo: 'What does this mean?',
    found: 'found',
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
    toolsTitle: 'Try asking',
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
    traceTitle: 'What I looked up',
    traceEmpty: 'Each lookup appears here, live, while I answer.',
    sources: 'Sources',
    noAmounts: 'No amounts to verify',
    ungrounded: 'Not found in the data:',
    flagged: 'suspicious text neutralized',
    thinking: 'Working…',
    errorGeneric: 'Something went wrong. Please try again.',
    welcome: 'Ask MSE Copilot about your courier cash',
    welcomeSub: 'Ask in plain English or French. I answer from your MSE Settle data with exact figures, check every amount, and link to the rows I used.',
    demoMode: 'Demo mode',
    demoModeHint: 'Runs on sample data without an AI provider, with the same lookups and checks. When an Anthropic API key is configured, MSE Copilot answers with Claude.',
    claudeMode: 'Claude',
    grounded: 'Verified',
    notGrounded: 'Not verified',
  },
  fr: {
    techDetails: 'Détails techniques',
    noRecords: 'Aucun enregistrement correspondant',
    noRecordsHint: 'Aucun enregistrement correspondant dans vos données : aucun montant à vérifier.',
    responseTime: 'Temps de réponse :',
    canSee: 'Ce que je peux voir',
    canSeeNote: 'Uniquement vos données : j’utilise vos droits, jamais plus.',
    seeBalances: 'Soldes des livreurs',
    seeStatements: 'Relevés et leur rapprochement',
    seeDisputes: 'Litiges',
    seeOrders: 'Commandes',
    seeGlossary: 'Le glossaire du règlement',
    starters: 'Commencez par une question',
    howItWorks: 'Comment ça marche',
    close: 'Fermer',
    topicCouriers: 'Livreurs',
    topicStatements: 'Relevés',
    topicDisputes: 'Litiges',
    topicOrders: 'Commandes',
    topicGlossary: 'Définitions',
    moreInfo: 'Qu’est-ce que cela veut dire ?',
    found: 'trouvé',
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
    toolsTitle: 'Essayez',
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
    traceTitle: 'Ce que j’ai consulté',
    traceEmpty: 'Chaque recherche s’affiche ici, en direct, pendant que je réponds.',
    sources: 'Sources',
    noAmounts: 'Aucun montant à vérifier',
    ungrounded: 'Introuvable dans les données :',
    flagged: 'texte suspect neutralisé',
    thinking: 'Analyse…',
    errorGeneric: 'Une erreur est survenue. Réessayez.',
    welcome: 'Interrogez MSE Copilot sur vos encaissements',
    welcomeSub: 'Posez vos questions en français ou en anglais. Je réponds à partir de vos données MSE Settle, avec des chiffres exacts vérifiés et un lien vers les lignes utilisées.',
    demoMode: 'Mode démo',
    demoModeHint: 'Fonctionne sur des données exemples, sans fournisseur d’IA, avec les mêmes recherches et vérifications. Avec une clé API Anthropic, MSE Copilot répond avec Claude.',
    claudeMode: 'Claude',
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

/** Clickable example questions in the side panel, grouped by topic (all answered by the offline model). */
export const TOPICS: Record<Lang, { key: I18nKey; questions: string[] }[]> = {
  en: [
    { key: 'topicCouriers', questions: ['Which courier owes me the most?', 'How much does Speedex owe me?'] },
    { key: 'topicStatements', questions: ['Why was statement #1 flagged?', 'List my statements'] },
    { key: 'topicDisputes', questions: ['Show open disputes over 400 MAD', 'Show open disputes'] },
    { key: 'topicOrders', questions: ['Delivered orders in Casablanca over 300 MAD'] },
    { key: 'topicGlossary', questions: ['What does amount mismatch mean?', 'Give me an overview'] },
  ],
  fr: [
    { key: 'topicCouriers', questions: ['Quel livreur me doit le plus ?', 'Combien me doit Speedex ?'] },
    { key: 'topicStatements', questions: ['Pourquoi le relevé n°1 a-t-il été signalé ?'] },
    { key: 'topicDisputes', questions: ['Montre les litiges ouverts de plus de 400 MAD'] },
    { key: 'topicGlossary', questions: ['Que signifie un écart ?'] },
  ],
};

export const SUGGESTIONS: Record<Lang, string[]> = {
  en: [
    'Which courier owes me the most?',
    'Show open disputes over 400 MAD',
    'Why was statement #1 flagged?',
    'What does amount mismatch mean?',
    'Give me an overview',
    'Why was statement #12 flagged?',
  ],
  fr: [
    'Quel livreur me doit le plus ?',
    'Montre les litiges ouverts de plus de 400 MAD',
    'Pourquoi le relevé n°1 a-t-il été signalé ?',
    'Que signifie un écart ?',
    'Combien me doit Speedex ?',
    'Quel livreur me devra le plus le mois prochain ?',
  ],
};

const STORAGE_KEY = 'settlement-copilot.lang';

@Injectable({ providedIn: 'root' })
export class I18nService {
  readonly lang = signal<Lang>(initialLang());
  readonly suggestions = computed(() => SUGGESTIONS[this.lang()]);
  readonly topics = computed(() => TOPICS[this.lang()]);

  t(key: I18nKey): string {
    return DICTIONARY[this.lang()][key];
  }

  /** "AMOUNT_MISMATCH" -> "Amount mismatch"; other text is returned unchanged. */
  label(raw: string): string {
    return LABELS[this.lang()][raw] ?? (/^[A-Z][A-Z_]+$/.test(raw) ? raw.charAt(0) + raw.slice(1).toLowerCase().replaceAll('_', ' ') : raw);
  }

  /** "2 amounts checked against your data" / "1 montant vérifié dans vos données". */
  verified(n: number): string {
    return this.lang() === 'fr'
      ? `${plural('fr', n, 'montant vérifié', 'montants vérifiés')} dans vos données`
      : `${plural('en', n, 'amount', 'amounts')} checked against your data`;
  }

  rows(n: number): string {
    return this.lang() === 'fr' ? plural('fr', n, 'ligne', 'lignes') : plural('en', n, 'row', 'rows');
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
