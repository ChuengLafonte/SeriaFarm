# 🌾 SeriaFarm Wiki

SeriaFarm is a high-performance farming system designed for large-scale RPG environments. It includes crop regeneration, custom growth speeds, and Farming Fortune integration.

## 🛠 Commands
| Command | Alias | Permission | Description |
|---------|-------|------------|-------------|
| `/sfarm` | `/farm` | `seriafarm.use` | Open farming stats |
| `/sfarm catalog` | - | `seriafarm.admin` | View all registered crops |
| `/sfarm reload` | - | `seriafarm.admin` | Reload configuration |

## 📊 Placeholders
- `%seriafarm_farming_fortune%`: Current total Farming Fortune.
- `%seriafarm_crops_harvested%`: Total crops harvested by player.

## ⚙️ Configuration Example

### config.yml
```yaml
settings:
  regeneration:
    enabled: true
    default_delay: 300 # seconds
  growth:
    speed_multiplier: 1.0

crops:
  wheat:
    regen_time: 60
    xp_gain: 5.0
    fortune_multiplier: 1.0
```
