package com.smartsoc.domain.intelligence;

/**
 * Traffic Light Protocol (FIRST TLP 2.0) — jusqu'où un renseignement
 * peut être rediffusé. C'est une contrainte de PARTAGE, pas de sécurité
 * technique : un IOC marqué RED reste exploitable dans la plateforme,
 * mais ne doit pas ressortir dans un rapport diffusé à l'extérieur.
 *
 * <p>Ce marquage voyage avec l'indicateur depuis le flux qui le fournit ;
 * la plateforme le conserve fidèlement pour que les futurs exports
 * (module Rapports) puissent le respecter.
 *
 * <p>Ordre déclaré du moins au plus restrictif.
 *
 * <p><b>Défaut volontaire à AMBER</b> quand un flux n'indique rien :
 * l'absence de marquage ne doit jamais être lue comme « librement
 * diffusable ». Un renseignement partagé trop largement ne se rattrape
 * pas — mieux vaut être trop prudent par défaut et desserrer sciemment.
 *
 * <p>TLP:AMBER+STRICT n'est pas modélisé : il n'ajoute une nuance que
 * pour les échanges inter-organisations, hors du périmètre actuel.
 */
public enum TlpMarking {

    /** Diffusion libre, sans restriction. */
    CLEAR,

    /** Diffusable dans la communauté, pas sur un canal public. */
    GREEN,

    /** Diffusable dans l'organisation et à ses clients, sur besoin d'en connaître. */
    AMBER,

    /** Strictement réservé aux destinataires nommés. */
    RED;

    /** Marquage retenu quand le flux n'en fournit aucun. */
    public static final TlpMarking DEFAULT = AMBER;
}
