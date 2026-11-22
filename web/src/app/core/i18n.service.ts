import { Injectable, computed, signal } from '@angular/core';
import { Lang } from './models';

const DICTIONARY = {
  en: {
    tagline: 'Ask your settlement data anything. Exact numbers, sources, no guessing.',
    signIn: 'Sign in',
    signingIn: 'Signing in…',
    username: 'Username',
    password: 'Password',
    demoAccounts: 'Demo accounts',
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
    welcomeSub: 'Answers come from the live settlement API, with your own permissions.',
    demoMode: 'Offline demo model',
    demoModeHint: 'Deterministic, no API key. Same tools, guards and streaming as Claude.',
    claudeMode: 'Claude',
    tools: 'tools',
    noKey: 'no API key',
    grounded: 'Grounded',
    notGrounded: 'Not grounded',
  },
  fr: {
    tagline: 'Interrogez vos données de règlement. Chiffres exacts, sources, aucune supposition.',
    signIn: 'Se connecter',
    signingIn: 'Connexion…',
    username: "Nom d'utilisateur",
    password: 'Mot de passe',
    demoAccounts: 'Comptes de démo',
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
    welcomeSub: "Les réponses viennent de l'API de règlement, avec vos propres droits.",
    demoMode: 'Modèle démo hors ligne',
    demoModeHint: "Déterministe, sans clé d'API. Mêmes outils, garde-fous et streaming que Claude.",
    claudeMode: 'Claude',
    tools: 'outils',
    noKey: "sans clé d'API",
    grounded: 'Vérifié',
    notGrounded: 'Non vérifié',
  },
} as const;

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
