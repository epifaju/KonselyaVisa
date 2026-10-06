package com.konselyavisa.privacy;

import java.util.Map;

/**
 * Canonical DPA template offered to client organizations (GDPR art. 28).
 * Legal PDF signature stays outside the app; acceptance is recorded per org.
 */
public final class OrganizationDpaCatalog {

    public static final String CURRENT_VERSION = "2026.09";
    public static final int DEFAULT_RETENTION_DAYS = 1825;
    public static final String PROCESSOR_LEGAL_NAME = "KonselyaVisa Platform Operator";

    public static final Map<String, String> SUMMARY_I18N = Map.of(
            "fr",
            "Accord de sous-traitance (art. 28 RGPD) : l’opérateur héberge et traite les dossiers pour le compte du responsable de traitement organisationnel. Conservation des données d’identité : 1825 jours après demande d’effacement, puis anonymisation automatique.",
            "pt",
            "Acordo de subprocessamento (art. 28 RGPD): o operador alojará e tratará os processos por conta do responsável pelo tratamento. Conservação dos dados de identidade: 1825 dias após pedido de apagamento, depois anonimização automática.",
            "en",
            "Data processing agreement (GDPR art. 28): the operator hosts and processes cases on behalf of the organizational controller. Identity data retention: 1825 days after an erasure request, then automatic anonymisation.");

    private OrganizationDpaCatalog() {}
}
