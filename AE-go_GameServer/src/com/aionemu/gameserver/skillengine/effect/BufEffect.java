/*
 * This file is part of aion-unique <aion-unique.com>.
 *
 *  aion-unique is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  aion-unique is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with aion-unique.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.aionemu.gameserver.skillengine.effect;

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlType;

import org.apache.log4j.Logger;

import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.stats.CreatureGameStats;
import com.aionemu.gameserver.model.gameobjects.stats.StatEffect;
import com.aionemu.gameserver.model.gameobjects.stats.modifiers.AddModifier;
import com.aionemu.gameserver.model.gameobjects.stats.modifiers.RateModifier;
import com.aionemu.gameserver.model.gameobjects.stats.modifiers.SetModifier;
import com.aionemu.gameserver.skillengine.change.Change;
import com.aionemu.gameserver.skillengine.model.Effect;
import com.aionemu.gameserver.skillengine.model.Skill;
import com.aionemu.gameserver.skillengine.model.SkillTemplate;

/**
 * @author ATracer
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "BufEffect")
public class BufEffect extends EffectTemplate
{
	private static final Logger log = Logger.getLogger(BufEffect.class);

	@XmlElements({
		@XmlElement(name = "change", type = Change.class)
	})
	protected List<Change> changes;

	@XmlAttribute(required = true)
	protected int duration;

	/**
	 * @return the changes
	 */
	public List<Change> getChanges()
	{
		return changes;
	}

	@Override
	public void apply(Skill skill)
	{
		List<Creature> effectedList = skill.getEffectedList();
		SkillTemplate template = skill.getSkillTemplate();
		Effect effect = new Effect(skill.getEffector().getObjectId(), template.getSkillId(),
			skill.getSkillLevel(), duration, this);

		for(Creature effected : effectedList)
		{
			effected.getEffectController().addEffect(effect);
		}
	}

	/**
	 * Will be called from effect controller when effect ends
	 */
	@Override
	public void endEffect(Creature effected, StatEffect effect, int skillId)
	{
		if (effect!=null)
		{
			effected.getGameStats().endEffect(effect);
		}
	}
	/**
	 * Will be called from effect controller when effect starts
	 */
	@Override
	public StatEffect startEffect(Creature effected, int skillId, int skillLvl)
	{
		if(changes == null)
			return null;

		CreatureGameStats<? extends Creature> cgs = effected.getGameStats();
		StatEffect effect = new StatEffect();
		for(Change change : changes)
		{
			if(change.getStat() == null)
			{
				log.warn("Skill stat has wrong name for skillid: " + skillId);
				continue;
			}

			int valueWithDelta = change.getValue() + change.getDelta() * skillLvl;

			switch(change.getFunc())
			{
				case ADD:
					effect.add(new AddModifier(change.getStat(),valueWithDelta,true));
					break;
				case PERCENT:
					effect.add(new RateModifier(change.getStat(),valueWithDelta,true));
					break;
				case REPLACE:
					effect.add(new SetModifier(change.getStat(),valueWithDelta));
					break;
			}
		}

		if (effect.getModifiers().size()>0)
		{
			cgs.addEffect(effect);
			return effect;
		}

		return null;
	}


}
