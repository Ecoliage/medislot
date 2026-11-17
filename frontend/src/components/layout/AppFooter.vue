<template>
  <footer>
    <span class="brand footer-brand">MediSlot.</span>
    <p>Votre parcours de soins, simplement.</p>
    <small>Application de formation · Données fictives · Horaires Europe/Paris</small>
  </footer>
</template>

<style scoped>
footer {
  background: white;
  border-top: 1px solid var(--line);
  padding: 34px max(32px, calc((100vw - 1280px) / 2));
  color: var(--muted);
}
.footer-brand {
  font-size: 24px;
}
footer p {
  font-size: 13px;
  margin: 10px 0;
}
footer small {
  font-size: 11px;
}
@media (max-width: 650px) {
  .footer-brand {
    font-size: 27px;
  }
}
</style>
