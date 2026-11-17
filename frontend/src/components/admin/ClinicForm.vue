<script setup lang="ts">
import {computed, onBeforeUnmount, onMounted, ref, watch} from 'vue';
import {
  clinicLogoUrl,
  createClinic,
  loadClinic,
  updateClinic,
  uploadClinicLogo,
} from '../../services/adminService';

const props = defineProps<{
  /** Absent: create a clinic. Present: edit that clinic. */
  slug?: string;
}>();
const emit = defineEmits<{saved: [slug: string]; cancel: []}>();

const MAX_LOGO_BYTES = 200 * 1024;
const LOGO_TYPES = ['image/png', 'image/svg+xml'];
const HEX = /^#[0-9a-f]{6}$/i;

/** Slug of the clinic being edited; set after a creation whose logo failed, so a retry updates. */
const editing = ref(props.slug ?? '');
const form = ref({name: '', slug: '', city: '', address: '', color: '#0b6bcb', adminEmail: ''});
const colorText = ref(form.value.color.toUpperCase());
const hasLogo = ref(false);
const logoFile = ref<File | null>(null);
const logoPreview = ref('');
const loading = ref(!!props.slug);
const saving = ref(false);
const error = ref('');
let slugEdited = !!props.slug;

const title = computed(() => (editing.value ? 'Modifier le cabinet' : 'Nouveau cabinet'));
const logoSrc = computed(() => logoPreview.value || (hasLogo.value && editing.value ? clinicLogoUrl(editing.value) : ''));

function slugify(value: string) {
  return value
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 40)
    .replace(/-+$/, '');
}

function onNameInput() {
  if (!slugEdited) form.value.slug = slugify(form.value.name);
}

watch(
  () => form.value.color,
  color => {
    if (color.toUpperCase() !== colorText.value.toUpperCase()) colorText.value = color.toUpperCase();
  },
);

function onColorText() {
  const value = colorText.value.trim();
  const hex = value.startsWith('#') ? value : '#' + value;
  if (HEX.test(hex)) form.value.color = hex.toLowerCase();
}

function onLogoChange(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0] ?? null;
  error.value = '';
  if (file && !LOGO_TYPES.includes(file.type)) {
    error.value = 'Le logo doit être un PNG ou un SVG.';
    input.value = '';
    return;
  }
  if (file && file.size > MAX_LOGO_BYTES) {
    error.value = 'Le logo dépasse 200 Ko.';
    input.value = '';
    return;
  }
  setLogo(file);
}

function setLogo(file: File | null) {
  if (logoPreview.value) URL.revokeObjectURL(logoPreview.value);
  logoFile.value = file;
  logoPreview.value = file ? URL.createObjectURL(file) : '';
}

async function submit() {
  if (!HEX.test(form.value.color)) {
    error.value = 'Couleur attendue au format #RRGGBB.';
    return;
  }
  saving.value = true;
  error.value = '';
  const changes = {
    name: form.value.name.trim(),
    city: form.value.city.trim(),
    address: form.value.address.trim() || null,
    color: form.value.color,
  };
  try {
    if (editing.value) {
      await updateClinic(editing.value, changes);
    } else {
      const created = await createClinic({...changes, slug: form.value.slug, adminEmail: form.value.adminEmail.trim()});
      editing.value = created.slug;
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e);
    saving.value = false;
    return;
  }
  try {
    if (logoFile.value) await uploadClinicLogo(editing.value, logoFile.value);
    emit('saved', editing.value);
  } catch (e) {
    const reason = e instanceof Error ? e.message : String(e);
    error.value = (props.slug ? '' : 'Le cabinet est créé, mais ') + 'le logo n’a pas été enregistré : ' + reason;
  } finally {
    saving.value = false;
  }
}

onMounted(async () => {
  if (!props.slug) return;
  try {
    const clinic = await loadClinic(props.slug);
    form.value = {
      name: clinic.name,
      slug: clinic.slug,
      city: clinic.city,
      address: clinic.address ?? '',
      color: clinic.color,
      adminEmail: '',
    };
    colorText.value = clinic.color.toUpperCase();
    hasLogo.value = clinic.hasLogo;
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e);
  } finally {
    loading.value = false;
  }
});

onBeforeUnmount(() => setLogo(null));
</script>

<template>
  <section class="clinic-form" aria-labelledby="clinic-form-title">
    <nav class="breadcrumb" aria-label="Fil d’Ariane">
      <button type="button" class="crumb" @click="emit('cancel')">Cabinets</button>
      <span aria-hidden="true">›</span>
      <span aria-current="page">{{ editing ? form.name || editing : 'Créer un cabinet' }}</span>
    </nav>
    <h2 id="clinic-form-title">{{ title }}</h2>

    <p v-if="loading" class="loading">Chargement du cabinet…</p>
    <form v-else @submit.prevent="submit">
      <div class="grid">
        <div class="column">
          <label>
            <span>Nom du cabinet <abbr title="obligatoire">*</abbr></span>
            <input
              v-model="form.name"
              required
              maxlength="120"
              autocomplete="off"
              placeholder="Cabinet de la Part-Dieu"
              @input="onNameInput"
            />
          </label>
          <label>
            <span>Identifiant (dans l’URL) <abbr title="obligatoire">*</abbr></span>
            <input
              v-model="form.slug"
              :disabled="!!editing"
              required
              maxlength="40"
              pattern="[a-z0-9]+(-[a-z0-9]+)*"
              title="Minuscules, chiffres et tirets"
              autocomplete="off"
              placeholder="part-dieu"
              aria-describedby="slug-help"
              @input="slugEdited = true"
            />
            <small id="slug-help">{{ editing ? 'L’identifiant ne change pas : c’est l’adresse du cabinet.' : '/c/' + (form.slug || 'identifiant') + '/' }}</small>
          </label>
          <label>
            <span>Ville <abbr title="obligatoire">*</abbr></span>
            <input v-model="form.city" required maxlength="80" autocomplete="off" placeholder="Lyon" />
          </label>
          <label>
            <span>Adresse</span>
            <input v-model="form.address" maxlength="160" autocomplete="off" placeholder="12 rue Servient" />
          </label>
        </div>

        <div class="column">
          <div class="field">
            <span id="color-label">Couleur</span>
            <div class="color-row">
              <input v-model="form.color" type="color" class="swatch" aria-labelledby="color-label" />
              <input
                v-model="colorText"
                class="hex"
                maxlength="7"
                aria-label="Couleur (hexadécimal)"
                spellcheck="false"
                @input="onColorText"
              />
            </div>
          </div>
          <div class="field">
            <span id="logo-label">Logo (PNG ou SVG, 200 Ko max)</span>
            <label class="file">
              <input type="file" accept="image/png,image/svg+xml,.png,.svg" aria-labelledby="logo-label" @change="onLogoChange" />
              <span class="file-name">{{ logoFile?.name ?? (hasLogo ? 'Remplacer le logo…' : 'Choisir un fichier…') }}</span>
            </label>
            <img v-if="logoSrc" :src="logoSrc" alt="Aperçu du logo" class="logo-preview" />
          </div>
          <label v-if="!editing">
            <span>E-mail du premier administrateur du cabinet <abbr title="obligatoire">*</abbr></span>
            <input
              v-model="form.adminEmail"
              type="email"
              required
              maxlength="254"
              autocomplete="off"
              placeholder="admin@part-dieu.test"
              aria-describedby="admin-help"
            />
            <small id="admin-help">Le compte doit déjà exister ; il devient membre du cabinet.</small>
          </label>
        </div>
      </div>

      <p v-if="error" class="error-text" role="alert">{{ error }}</p>
      <div class="actions">
        <button class="dark-button" type="submit" :disabled="saving">
          {{ saving ? 'Enregistrement…' : editing ? 'Enregistrer' : 'Créer le cabinet' }}
        </button>
        <button class="outline-button" type="button" :disabled="saving" @click="emit('cancel')">Annuler</button>
      </div>
    </form>
  </section>
</template>

<style scoped>
.clinic-form {
  margin-bottom: 40px;
}
.breadcrumb {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: var(--muted);
}
.crumb {
  background: none;
  padding: 0;
  color: var(--muted);
  font: inherit;
  cursor: pointer;
}
.crumb:hover {
  color: var(--navy);
  text-decoration: underline;
}
h2 {
  margin: 14px 0 18px;
}
.grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 24px;
}
.column {
  display: flex;
  flex-direction: column;
  gap: 18px;
}
label,
.field {
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 14px;
  color: #4d5b66;
}
abbr {
  text-decoration: none;
}
small {
  color: var(--muted);
  font-size: 12px;
}
input:not([type='color']):not([type='file']) {
  border: 1px solid #a9b6c0;
  border-radius: 6px;
  padding: 11px 12px;
  font-size: 15px;
  background: white;
  min-width: 0;
}
input:disabled {
  background: #eef2f5;
  color: var(--muted);
}
.color-row {
  display: flex;
  gap: 12px;
  align-items: center;
}
.swatch {
  width: 38px;
  height: 38px;
  padding: 2px;
  border: 1px solid #a9b6c0;
  border-radius: 6px;
  background: white;
  cursor: pointer;
}
.hex {
  width: 138px;
  text-transform: uppercase;
}
.file {
  position: relative;
  border: 1px solid #a9b6c0;
  border-radius: 6px;
  padding: 11px 12px;
  background: white;
  cursor: pointer;
}
.file:focus-within {
  outline: 2px solid var(--blue);
  outline-offset: 1px;
}
.file input {
  position: absolute;
  inset: 0;
  opacity: 0;
  cursor: pointer;
}
.file-name {
  color: #4d5b66;
  font-size: 15px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: block;
}
.logo-preview {
  max-width: 120px;
  max-height: 60px;
  object-fit: contain;
  align-self: flex-start;
  border: 1px solid var(--line);
  border-radius: 6px;
  padding: 4px;
  background: white;
}
.actions {
  display: flex;
  gap: 16px;
  margin-top: 24px;
}
.dark-button {
  background: #1e2a33;
  color: white;
  border-radius: 6px;
  padding: 12px 20px;
  font-weight: 500;
  font-size: 15px;
}
.dark-button:hover {
  background: #2d3d49;
}
.outline-button {
  background: white;
  color: #1e2a33;
  border: 1px solid #1e2a33;
  border-radius: 6px;
  padding: 11px 18px;
  font-size: 15px;
}
.outline-button:hover {
  background: #f0f3f5;
}
button:disabled {
  opacity: 0.6;
  cursor: default;
}
@media (max-width: 650px) {
  .grid {
    grid-template-columns: 1fr;
    gap: 18px;
  }
}
</style>
