-- WAZUH_AGENT_FIREWALL_DROP_REQUESTED (35 caracteres) depasse VARCHAR(30) :
-- l'insertion echouait silencieusement en erreur SQL des le premier blocage
-- d'IP reel. Marge portee a 64 pour les prochains types d'action.
ALTER TABLE audit_log ALTER COLUMN action TYPE VARCHAR(64);
