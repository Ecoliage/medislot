<script setup lang="ts">
import {computed} from 'vue';
import {formatDistance} from '../geo';
import {specialties, timeLabel, dateLabel, type Professional, type Slot} from '../types';
const props = defineProps<{professional: Professional; slots: Slot[]}>();
defineEmits<{choose: [professional: Professional, slot: Slot]; profile: [professional: Professional]}>();
const days = computed(() => {
  const m = new Map<string, Slot[]>();
  for (const s of props.slots) {
    const day = dateLabel(s.startsAt);
    if (!m.has(day)) m.set(day, []);
    m.get(day)!.push(s);
  }
  return [...m.entries()].slice(0, 3);
});
const initials = computed(() =>
  props.professional.name
    .replace('Dr ', '')
    .split(' ')
    .map(s => s[0])
    .join(''),
);
</script>
<template>
  <article class="doctor-card">
    <div class="doctor-info">
      <div class="identity-row">
        <div class="avatar" aria-hidden="true">{{ initials }}</div>
        <div>
          <button class="name-link" @click="$emit('profile', professional)">{{ professional.name }}</button>
          <p class="specialty">{{ specialties[professional.specialty] }}</p>
        </div>
      </div>
      <p class="address">{{ professional.address }}<br />{{ professional.city }}</p>
      <p v-if="professional.distanceKm != null" class="distance">À {{ formatDistance(professional.distanceKm) }}</p>
      <p class="price">Consultation au cabinet · {{ professional.fee }} €</p>
      <button class="text-button" @click="$emit('profile', professional)">
        Voir la fiche du praticien <span aria-hidden="true">↗</span>
      </button>
    </div>
    <div class="availability">
      <p class="availability-title">Prochaines disponibilités</p>
      <div v-if="days.length" class="days">
        <div v-for="[day, slotsOfDay] in days" :key="day" class="day">
          <h3>{{ day }}</h3>
          <button
            v-for="slot in slotsOfDay.slice(0, 4)"
            :key="slot.id"
            class="slot"
            :aria-label="`Réserver avec ${professional.name} le ${day} à ${timeLabel(slot.startsAt)}`"
            @click="$emit('choose', professional, slot)"
          >
            {{ timeLabel(slot.startsAt) }}
          </button>
        </div>
      </div>
      <p v-else class="empty-small">Aucun créneau disponible dans les 14 prochains jours.</p>
    </div>
  </article>
</template>

<style scoped>
.doctor-card {
  background: white;
  border: 1px solid var(--line);
  border-radius: 13px;
  display: grid;
  grid-template-columns: 1fr 1.1fr;
  box-shadow: 0 3px 7px #153c5010;
  overflow: hidden;
}
.doctor-info {
  padding: 27px 24px;
}
.identity-row {
  display: flex;
  align-items: center;
  gap: 15px;
  margin-bottom: 22px;
}
.avatar {
  width: 55px;
  height: 55px;
  flex-shrink: 0;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: #e6f2f7;
  color: #1373a7;
  font-size: 19px;
  font-weight: 700;
}
.name-link {
  color: #096daa;
  background: none;
  text-align: left;
  padding: 0;
  font-size: 18px;
  font-weight: 700;
}
.name-link:hover {
  text-decoration: underline;
}
.address {
  font-size: 14px;
  line-height: 1.6;
  margin-bottom: 13px;
  color: #405d70;
}
.price {
  font-size: 12px;
  color: #597487;
}
.availability {
  border-left: 1px solid #e8eef3;
  background: #fcfdfe;
  padding: 24px 22px;
}
.availability-title {
  font-size: 12px;
  color: #4c687b;
  font-weight: 600;
  margin-bottom: 17px;
}
.days {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 9px;
}
.day h3 {
  font-family: inherit;
  font-size: 11px;
  line-height: 1.5;
  text-transform: capitalize;
  min-height: 34px;
  text-align: center;
  color: #355369;
}
.slot {
  display: block;
  width: 100%;
  margin: 0 0 8px;
  background: #e5f2fa;
  color: #065d94;
  border-radius: 6px;
  font-weight: 700;
  font-size: 13px;
  padding: 11px 4px;
}
.slot:hover {
  background: #0873b9;
  color: white;
}
.empty-small {
  font-size: 13px;
  color: var(--muted);
}
@media (max-width: 1350px) {
  .doctor-info {
    padding: 22px 18px;
  }
  .availability {
    padding: 22px 16px;
  }
}
@media (max-width: 650px) {
  .doctor-card {
    grid-template-columns: 1fr;
  }
  .availability {
    border-left: 0;
    border-top: 1px solid var(--line);
  }
  .identity-row {
    margin-bottom: 17px;
  }
  .day h3 {
    min-height: 20px;
  }
  .slot {
    padding: 12px;
  }
}
.distance {
  font-size: 13px;
  font-weight: 700;
  color: #076b9e;
  margin-bottom: 8px;
}
</style>
