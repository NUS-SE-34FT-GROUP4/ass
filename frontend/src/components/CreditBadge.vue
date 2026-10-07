<template>
  <span v-if="level" class="credit-badge" :class="'level-' + level" :title="levelName">
    <span class="badge-icon">{{ getLevelIcon(level) }}</span>
    <span class="badge-text">{{ levelName }}</span>
  </span>
</template>

<script>
export default {
  name: 'CreditBadge',
  props: {
    level: {
      type: Number,
      default: 1,
    },
  },
  computed: {
    levelName() {
      const names = {
        1: 'Newcomer',
        2: 'Bronze Seller',
        3: 'Silver Seller',
        4: 'Gold Seller',
        5: 'Diamond Seller',
      };
      return names[this.level] || 'Newcomer';
    },
  },
  methods: {
    getLevelIcon(level) {
      const icons = {
        1: '🌱',
        2: '🥉',
        3: '🥈',
        4: '🥇',
        5: '💎',
      };
      return icons[level] || '🌱';
    },
  },
};
</script>

<style scoped>
.credit-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
}

.badge-icon {
  font-size: 14px;
}

.badge-text {
  font-weight: 600;
}

/* Colours per level */
.credit-badge.level-1 {
  background: linear-gradient(135deg, #e8f5e9 0%, #c8e6c9 100%);
  color: #2e7d32;
}

.credit-badge.level-2 {
  background: linear-gradient(135deg, #fff3e0 0%, #ffe0b2 100%);
  color: #e65100;
}

.credit-badge.level-3 {
  background: linear-gradient(135deg, #f5f5f5 0%, #e0e0e0 100%);
  color: #424242;
}

.credit-badge.level-4 {
  background: linear-gradient(135deg, #fff9c4 0%, #ffeb3b 100%);
  color: #f57f17;
}

.credit-badge.level-5 {
  background: linear-gradient(135deg, #e1f5fe 0%, #81d4fa 100%);
  color: #01579b;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}
</style>

